package com.example.akplayer;

import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.os.Build;

/** Adapts Android's DynamicsProcessing EQ, with a native Equalizer fallback. */
final class DeviceEqualizer {
    private static final int[] CENTERS={30,45,60,90,120,180,250,380,500,750,1000,1500,2000,3000,4000,6000,8000,12000,14000,18000};
    private DynamicsProcessing dynamics;
    private Equalizer fallback;

    DeviceEqualizer(int session){
        if(Build.VERSION.SDK_INT>=28){
            try {
                DynamicsProcessing.Config config=new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,2,true,CENTERS.length,false,0,false,0,false).setPreferredFrameDuration(40f).build();
                dynamics=new DynamicsProcessing(0,session,config);
                if(dynamics.getConfig().getPreEqBandCount()!=CENTERS.length||!dynamics.hasControl())throw new IllegalStateException("20-band EQ is unavailable");
                for(int ch=0;ch<dynamics.getChannelCount();ch++){
                    DynamicsProcessing.Eq eq=dynamics.getPreEqByChannelIndex(ch);
                    for(int i=0;i<CENTERS.length;i++){
                        float cutoff=i==CENTERS.length-1?22000f:(float)Math.sqrt((double)CENTERS[i]*CENTERS[i+1]);
                        eq.setBand(i,new DynamicsProcessing.EqBand(true,cutoff,0f));
                    }
                    dynamics.setPreEqByChannelIndex(ch,eq);
                }
                return;
            }catch(RuntimeException e){if(dynamics!=null){dynamics.release();dynamics=null;}android.util.Log.w("CrimsonEQ","Dynamics EQ unavailable; trying native EQ",e);}
        }
        fallback=new Equalizer(0,session);
    }
    boolean isDynamics(){return dynamics!=null;}
    short getNumberOfBands(){return dynamics!=null?(short)CENTERS.length:fallback.getNumberOfBands();}
    int getCenterFreq(short band){return dynamics!=null?CENTERS[band]*1000:fallback.getCenterFreq(band);}
    short[] getBandLevelRange(){return dynamics!=null?new short[]{-500,500}:fallback.getBandLevelRange();}
    short getBandLevel(short band){return dynamics!=null?(short)Math.round(dynamics.getPreEqByChannelIndex(0).getBand(band).getGain()*100f):fallback.getBandLevel(band);}
    void setBandLevel(short band,short value){
        if(dynamics==null){fallback.setBandLevel(band,value);return;}
        for(int ch=0;ch<dynamics.getChannelCount();ch++){DynamicsProcessing.Eq eq=dynamics.getPreEqByChannelIndex(ch);DynamicsProcessing.EqBand b=eq.getBand(band);b.setGain(value/100f);eq.setBand(band,b);dynamics.setPreEqByChannelIndex(ch,eq);}
    }
    int setEnabled(boolean enabled){return dynamics!=null?dynamics.setEnabled(enabled):fallback.setEnabled(enabled);}
    boolean getEnabled(){return dynamics!=null?dynamics.getEnabled():fallback.getEnabled();}
    boolean hasControl(){return dynamics!=null?dynamics.hasControl():fallback.hasControl();}
    void release(){if(dynamics!=null)dynamics.release();if(fallback!=null)fallback.release();}
}
