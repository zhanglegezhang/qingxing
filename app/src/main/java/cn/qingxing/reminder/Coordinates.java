package cn.qingxing.reminder;

/** BD-09 map coordinates and Android WGS84 test-provider coordinates. */
final class Coordinates {
    static final double PI=Math.PI,A=6378245.0,EE=0.00669342162296594323;
    static boolean valid(double lat,double lon){return !Double.isNaN(lat)&&!Double.isInfinite(lat)&&!Double.isNaN(lon)&&!Double.isInfinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    static boolean china(double lat,double lon){return lon>=72.004&&lon<=137.8347&&lat>=0.8293&&lat<=55.8271;}
    static double[] wgsToGcj(double lat,double lon){
        if(!china(lat,lon))return new double[]{lat,lon};
        double x=lon-105,y=lat-35;
        double dlat=-100+2*x+3*y+.2*y*y+.1*x*y+.2*Math.sqrt(Math.abs(x));
        dlat+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;
        dlat+=(20*Math.sin(y*PI)+40*Math.sin(y/3*PI))*2/3;
        dlat+=(160*Math.sin(y/12*PI)+320*Math.sin(y*PI/30))*2/3;
        double dlon=300+x+2*y+.1*x*x+.1*x*y+.1*Math.sqrt(Math.abs(x));
        dlon+=(20*Math.sin(6*x*PI)+20*Math.sin(2*x*PI))*2/3;
        dlon+=(20*Math.sin(x*PI)+40*Math.sin(x/3*PI))*2/3;
        dlon+=(150*Math.sin(x/12*PI)+300*Math.sin(x/30*PI))*2/3;
        double rad=lat/180*PI,magic=1-EE*Math.sin(rad)*Math.sin(rad),sqrt=Math.sqrt(magic);
        return new double[]{lat+dlat*180/((A*(1-EE))/(magic*sqrt)*PI),lon+dlon*180/(A/sqrt*Math.cos(rad)*PI)};
    }
    static double[] wgsToBd(double lat,double lon){
        if(!china(lat,lon))return new double[]{lat,lon};
        double[] g=wgsToGcj(lat,lon);double x=g[1],y=g[0],z=Math.sqrt(x*x+y*y)+.00002*Math.sin(y*PI*3000/180),t=Math.atan2(y,x)+.000003*Math.cos(x*PI*3000/180);
        return new double[]{z*Math.sin(t)+.006,z*Math.cos(t)+.0065};
    }
    static double[] bdToWgs(double lat,double lon){
        if(!valid(lat,lon))throw new IllegalArgumentException("坐标无效");
        if(!china(lat,lon))return new double[]{lat,lon};
        double x=lon-.0065,y=lat-.006,z=Math.sqrt(x*x+y*y)-.00002*Math.sin(y*PI*3000/180),t=Math.atan2(y,x)-.000003*Math.cos(x*PI*3000/180);
        double glat=z*Math.sin(t),glon=z*Math.cos(t),wl=glat,wn=glon;
        for(int i=0;i<8;i++){double[] g=wgsToGcj(wl,wn);wl-=g[0]-glat;wn-=g[1]-glon;}
        return new double[]{wl,wn};
    }
}
