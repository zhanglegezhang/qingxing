"""One-time source edit for the confirmed attendance-only scope."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=root/'app/src/main/java/cn/qingxing/reminder/MainActivity.java'
s=main.read_text(encoding='utf-8-sig')
start=s.index('    private void medicines()')
end=s.index('    interface TimeResult',start)
s=s[:start]+s[end:]
start=s.index('    void saveMedicine(')
end=s.index('    void settings()',start)
s=s[:start]+s[end:]
s=s.replace('Math.min(4,b.getInt("page",0))','Math.min(3,b.getInt("page",0))')
s=s.replace('"今天，轻松一点","设置好，安心出发","重要的事，安心记下","你的工作与休息","让每一次提醒到达"','"今天，轻松一点","设置好，安心出发","你的工作与休息","让每次打卡提醒到达"')
s=s.replace('else if(page==2)medicines();else if(page==3)new Screens(this).calendar();','else if(page==2)new Screens(this).calendar();')
s=s.replace('"今天","打卡","提醒","日历","设置"','"今天","打卡","日历","设置"')
s=s.replace('不会自动确认事项完成','需要你手动确认已打卡').replace('轻醒 0.3.0 · 百度地图版','轻醒 0.4.0 · 打卡提醒')
s=s.replace('测试声音、振动和 5 分钟重复','测试打卡声音、振动和 5 分钟重复').replace('轻醒 · 提醒测试','轻醒 · 打卡提醒测试')
s=s.replace('提醒事项、完成记录和公司设置保存在手机。','打卡记录和公司设置保存在手机。').replace('；提醒事项不会发送给百度','；打卡完成记录不会发送给百度')
s=s.replace('保留已保存的位置、提醒与记录','保留公司设置和打卡记录').replace('导入会替换公司设置和提醒事项','导入会替换公司设置').replace('设置已导出；备份包含公司位置和提醒事项，请妥善保存','设置已导出；备份包含公司位置，请妥善保存')
s=s.replace('JSONObject backup=new JSONObject();Store.put(backup,"schema",2);Store.put(backup,"config",store.config());Store.put(backup,"meds",store.meds());','JSONObject backup=makeBackup(store);')
start=s.index('                JSONObject pending=store.pending();',s.index('else if(request==41)'))
end=s.index('\n            }',start)
s=s[:start]+'                restoreBackup(this,backup);draft.clear();toast("公司设置已导入，打卡监测保持暂停");render();'+s[end:]
s=s.replace('if(b.getInt("schema")!=1&&b.getInt("schema")!=2)','if(b.getInt("schema")!=1&&b.getInt("schema")!=2&&b.getInt("schema")!=3)')
start=s.index('        JSONArray m=b.getJSONArray("meds");')
end=s.index('\n    }',start)
s=s[:start]+s[end:]
pos=s.index('    static void validateBackup')
s=s[:pos]+'''    static JSONObject makeBackup(Store s){JSONObject b=new JSONObject();Store.put(b,"schema",3);Store.put(b,"config",s.config());return b;}
    static void restoreBackup(Context c,JSONObject b)throws Exception{
        validateBackup(b);Store s=new Store(c);JSONObject pending=s.pending();Iterator<String> it=pending.keys();while(it.hasNext())Alerts.cancel(c,it.next());
        s.pending(new JSONObject());s.enabled(false);c.stopService(new Intent(c,MonitorService.class));
        JSONObject clean=new JSONObject(),source=b.getJSONObject("config");
        for(String key:new String[]{"lat","lon","coordType","radius","ssid","bssids","weakRssi","amStart","amEnd","pmStart","pmEnd","calendar"})if(source.has(key))Store.put(clean,key,source.get(key));
        s.saveConfig(clean);s.day(new JSONObject());Planner.rebuild(c);
    }
'''+s[pos:]
s=s.replace('        if(c.has("lat")){','        if(c.has("lat")!=c.has("lon"))throw new IllegalArgumentException("经纬度需要成对填写");\n        if(c.has("lat")){')
main.write_text(s,encoding='utf-8')
path=root/'app/src/main/java/cn/qingxing/reminder/Screens.java'
s=path.read_text(encoding='utf-8-sig')
start=s.index('        JSONArray meds=store.meds();')
end=s.index('        if(!Planner.exact(a)',start)
s=s[:start]+'''        if(!store.config().has("lat")){LinearLayout setup=card("先设置公司与打卡提醒","选好办公楼、绑定公司 Wi-Fi，再设置上下班提醒时段。");a.button(setup,"设置公司与打卡提醒",()->go(1),true);}
'''+s[end:]
s=s.replace('if(p==null)continue;','if(!AttendanceData.validPending(key,p))continue;')
s=s.replace('type.equals("med")?"✓ 已完成":type.equals("attendance")?"✓ 我已打卡":"✓ 完成测试"','type.equals("attendance")?"✓ 我已打卡":"✓ 结束打卡测试"')
s=s.replace('go(3)','go(2)').replace('go(4)','go(3)')
start=s.index('    void medicines(){')
end=s.index('    void calendar(){',start)
s=s[:start]+s[end:]
s=s.replace('本年安排待取得：自动打卡暂停，定时提醒照常。你也可以明确设置某天是否上班。','本年安排待取得：自动打卡暂停。你也可以明确设置某天是否上班。')
start=s.index('            else if(index==2){Path bell=')
end=s.index('            else if(index==3)',start)
s=s[:start]+s[end:]
s=s.replace('else if(index==3){c.drawRoundRect','else if(index==2){c.drawRoundRect')
path.write_text(s,encoding='utf-8')
rules=root/'app/src/main/java/cn/qingxing/reminder/Rules.java'
s=rules.read_text(encoding='utf-8-sig');start=s.index('    public static long nextDose(');end=s.index('    public static final class Sample',start);rules.write_text(s[:start]+s[end:],encoding='utf-8')
test=root/'tests/RulesTest.java'
s=test.read_text(encoding='utf-8-sig');start=s.index('        check(Rules.parseTimes(');end=s.index('        Rules.Detector approach=',start);test.write_text(s[:start]+s[end:],encoding='utf-8')
privacy=root/'app/src/main/java/cn/qingxing/reminder/BaiduSupport.java'
s=privacy.read_text(encoding='utf-8-sig').replace('提醒事项与完成记录仍保存在本机','打卡完成记录仍保存在本机').replace('不同意仍可使用定时提醒','不同意时，自动位置监测不会启用；你仍可查看日历和手动确认打卡');privacy.write_text(s,encoding='utf-8')
manifest=root/'app/src/main/AndroidManifest.xml'
s=manifest.read_text(encoding='utf-8-sig').replace('android:versionCode="4" android:versionName="0.3.0"','android:versionCode="5" android:versionName="0.4.0"');manifest.write_text(s,encoding='utf-8')
for name in ['build.ps1','test-android.ps1','test-upgrade.ps1','test-smoke.ps1','test-baidu.ps1']:
    path=root/name;s=path.read_text(encoding='utf-8-sig').replace('Qingxing-0.3.0.apk','Qingxing-0.4.0.apk').replace("'QingxingSmoke'","'QingxingSmoke16'").replace('QingxingSmoke AVD','QingxingSmoke16 AVD')
    path.write_text(s,encoding='utf-8-sig')
