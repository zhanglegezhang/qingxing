package cn.qingxing.reminder;
import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.net.wifi.*;
/** SSID observation only; no scans, GPS or sensors. */
final class WifiConnection {
    static final class State {final String name,detail;final boolean connected;State(String n,boolean c,String d){name=n;connected=c;detail=d;}}
    static boolean permitted(Context c,boolean background){return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED&&(!background||android.os.Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED);}
    static String clean(String s){if(s==null)return "";return s.length()>1&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;}
    @SuppressWarnings("deprecation") static State read(Context c,String bound){
        try{WifiManager m=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(m==null||!m.isWifiEnabled())return new State("",false,"Wi-Fi 已关闭，按未连接处理");
            WifiInfo info=m.getConnectionInfo();if(info==null||info.getNetworkId()==-1||info.getSupplicantState()!=SupplicantState.COMPLETED)return new State("",false,"尚未连接完成，按未连接处理");String name=clean(info.getSSID());
            if(name.isEmpty()||name.equals(WifiManager.UNKNOWN_SSID))return new State("",false,"未连接或名称读取失败，按未连接处理");
            boolean match=Rules.matches(bound,name);return new State(name,match,match?"已连接公司 Wi-Fi":"当前连接其他 Wi-Fi，按未连接处理");
        }catch(RuntimeException e){return new State("",false,"读取失败（"+e.getClass().getSimpleName()+"），按未连接处理");}
    }
}
