package com.example.akplayer;

import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.os.Build;

/** Adapts Android's DynamicsProcessing EQ, with a native Equalizer fallback. */
final class DeviceEqualizer {
    private static final int[] CENTERS=EqPresets.centerFrequencies();
    private DynamicsProcessing dynamics;
    private Equalizer fallback;

    DeviceEqualizer(int session){
        if(Build.VERSION.SDK_INT>=28){
            try {
                DynamicsProcessing.Eq initialEq=new DynamicsProcessing.Eq(true,true,CENTERS.length);
                for(int i=0;i<CENTERS.length;i++){
                    float cutoff=i==CENTERS.length-1?22000f:(float)Math.sqrt((double)CENTERS[i]*CENTERS[i+1]);
                    initialEq.setBand(i,new DynamicsProcessing.EqBand(true,cutoff,0f));
                }
                DynamicsProcessing.Config config=new DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,2,true,CENTERS.length,false,0,false,0,false).setPreferredFrameDuration(40f).setPreEqAllChannelsTo(initialEq).build();
                dynamics=new DynamicsProcessing(0,session,config);
                if(dynamics.getConfig().getPreEqBandCount()!=CENTERS.length||!dynamics.hasControl())throw new IllegalStateException("20-band EQ is unavailable");
                return;
            }catch(RuntimeException e){if(dynamics!=null){dynamics.release();dynamics=null;}android.util.Log.w("CrimsonEQ","Dynamics EQ unavailable; trying native EQ",e);}
        }
        try {
            fallback=new Equalizer(0,session);
            if(!fallback.hasControl()||fallback.getNumberOfBands()==0)
                throw new IllegalStateException("Native EQ control is unavailable");
        } catch(RuntimeException e) {
            if(fallback!=null){fallback.release();fallback=null;}
            throw e;
        }
    }
    boolean isDynamics(){return dynamics!=null;}
    short getNumberOfBands(){return dynamics!=null?(short)CENTERS.length:fallback.getNumberOfBands();}
    int getCenterFreq(short band){return dynamics!=null?CENTERS[band]*1000:fallback.getCenterFreq(band);}
    short[] getBandLevelRange(){return dynamics!=null?new short[]{-500,500}:fallback.getBandLevelRange();}
    short getBandLevel(short band){return Build.VERSION.SDK_INT>=28&&dynamics!=null?(short)Math.round(dynamics.getPreEqBandByChannelIndex(0,band).getGain()*100f):fallback.getBandLevel(band);}
    void setBandLevel(short band,short value){
        if(Build.VERSION.SDK_INT<28||dynamics==null){fallback.setBandLevel(band,value);return;}
        DynamicsProcessing.EqBand b=dynamics.getPreEqBandByChannelIndex(0,band);
        if(Math.abs(b.getGain()-value/100f)<0.001f)return;
        b.setGain(value/100f);
        dynamics.setPreEqBandAllChannelsTo(band,b);
    }
    int setEnabled(boolean enabled){return dynamics!=null?dynamics.setEnabled(enabled):fallback.setEnabled(enabled);}
    boolean getEnabled(){return dynamics!=null?dynamics.getEnabled():fallback.getEnabled();}
    boolean hasControl(){return dynamics!=null?dynamics.hasControl():fallback.hasControl();}
    void release(){if(dynamics!=null)dynamics.release();if(fallback!=null)fallback.release();}
}
