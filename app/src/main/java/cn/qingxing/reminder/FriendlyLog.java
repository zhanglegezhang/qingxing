package cn.qingxing.reminder;
import org.json.JSONObject;

/** Human-readable presentation of current and historical diagnostics; raw local records stay intact. */
final class FriendlyLog {
    static String message(JSONObject line){
        String module=line.optString("module"),event=line.optString("event"),detail=line.optString("detail");boolean error=line.optBoolean("error");
        String card=(detail.startsWith("am")||detail.contains("上班"))?"上班卡":(detail.startsWith("pm")||detail.contains("下班"))?"下班卡":"打卡";
        if(event.contains("系统事件")){
            if(event.contains("BOOT_COMPLETED"))return "手机刚刚启动，已重新检查提醒安排。";
            if(event.contains("MY_PACKAGE_REPLACED"))return "轻醒已更新，正在继续使用原来的设置。";
            if(event.contains("TIME"))return "手机时间有变化，已重新检查提醒时间。";
            return "手机状态有变化，已重新检查提醒安排。";
        }
        if(module.equals("监控通知"))return error?"后台通知设置不太对，请到手机通知设置中检查轻醒。":detail.contains("声震请求=true")?"后台刚刚发出了监控提示。":"正在后台帮你留意打卡，监控时不会响铃或振动。";
        if(module.equals("Wi-Fi"))return event.equals("已连接")?"现在连着公司 Wi-Fi。":"现在没有连上公司 Wi-Fi，稍后会继续查看。";
        if(module.equals("检查"))return detail.contains("符合提醒条件=true")?"刚查看了 Wi-Fi，到了提醒时间会提醒你打"+card+"。":"刚查看了 Wi-Fi，现在不用发出打卡提醒。";
        if(event.equals("开始新一轮"))return "开始留意"+card+"，满一分钟后再根据 Wi-Fi 情况提醒。";
        if(module.equals("声震")){
            if(event.startsWith("开始")){
                java.util.regex.Matcher duration=java.util.regex.Pattern.compile("(\\d+)\\s*秒").matcher(event);String seconds=duration.find()?duration.group(1):"5";
                if(error)return "这次没有响铃或振动，请检查手机音量、勿扰和轻醒通知设置。";
                if(detail.contains("响铃=false"))return "正在振动提醒你打卡，持续 "+seconds+" 秒；手机当前没有发出铃声。";
                if(detail.contains("振动请求=false"))return "正在响铃提醒你打卡，持续 "+seconds+" 秒；手机当前没有振动。";
                return "正在提醒你打卡，本次响铃和振动持续 "+seconds+" 秒。";
            }
            if(event.equals("已停止"))return detail.contains("已满")?"本次提醒已结束。":"本次响铃和振动已停止。";
            return "这次提醒没能正常发出，请检查手机音量、勿扰和轻醒通知设置。";
        }
        if(module.equals("企微"))return event.contains("失败")?"没有打开企业微信，也没有记为已打卡。请检查企微是否已安装，或是否被系统限制。":detail.contains("记为已打卡")?"已打开企业微信，并记为已打卡。记得在企微完成实际打卡。":"已请求打开企业微信，请完成实际打卡。";
        if(module.equals("通知"))return "你已点击通知确认"+card+"。";
        if(module.equals("确认"))return event.contains("拒绝")?"这条提醒已经结束，请查看今天最新的打卡状态。":"已收到你的打卡确认。";
        if(module.equals("状态"))return "已记为"+card+"完成，对应提醒已停止。";
        if(module.equals("界面"))return detail.contains("未确认")?"首页已更新为未打卡。":"首页已更新为已打卡。";
        if(module.equals("提醒")){
            if(event.equals("已停止"))return "现在不需要继续提醒，已停止本次打卡提醒。";
            return error?"没能正常发出打卡提醒，请检查轻醒的通知设置。":"已发出打卡提醒，请留意手机。静音或勿扰可能影响效果。";
        }
        if(module.equals("监测")||module.equals("监控")){
            if(event.contains("启动失败")||event.contains("检查失败"))return "暂时没能继续查看 Wi-Fi，请打开轻醒检查权限。";
            if(event.contains("启动"))return "开始在后台留意公司 Wi-Fi，每 10 秒查看一次。";
            if(event.contains("停止"))return detail.contains("时段")?"监控已暂停，到了下个提醒时段再继续。":"监控已暂停，现在不会查看 Wi-Fi。";
        }
        if(module.equals("计划"))return "提醒时间暂时没安排成功，请在手机设置中允许轻醒使用“闹钟和提醒”。";
        if(module.equals("授权"))return error?"还没有完成必要授权，请到设置页按提示检查。":"授权状态已更新，会按最新设置决定是否继续监控。";
        if(module.equals("自动保存"))return "这次设置没有保存成功，继续使用原来的设置，请检查 Wi-Fi 名称或提醒时段。";
        if(module.equals("开关"))return error||detail.contains("等待")?"提醒已开启，完成必要授权后才会开始监控。":"提醒已开启，会在上班日和设定时段内帮你留意打卡。";
        if(event.startsWith("调休检查"))return event.contains("失败")||event.contains("未完成")?"这次没有更新好调休安排，先继续使用原来的日历。":"已检查工作日和调休安排。";
        if(event.startsWith("每分钟提醒"))return "还没确认"+(event.contains("上班")?"上班卡":"下班卡")+"，这次继续提醒你。";
        if(event.startsWith("手动确认"))return "已记为"+(event.contains("上班")?"上班卡":"下班卡")+"完成，对应提醒已停止。";
        if(event.startsWith("已取消"))return "已取消"+(event.contains("上班")?"上班卡":"下班卡")+"确认，到了提醒时段会重新留意。";
        if(event.startsWith("已自动保存"))return event+"。";
        if(event.contains("已暂停"))return "已暂停监控和打卡提醒。";
        // Unknown historical diagnostics still get a readable summary, never identifiers or exception stacks.
        return error?"这次操作没有完成，请检查设置页中的权限和提醒设置。":"轻醒刚刚检查了运行状态，会按你的设置继续提醒。";
    }
}
