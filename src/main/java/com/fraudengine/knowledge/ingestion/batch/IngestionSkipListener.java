package com.fraudengine.knowledge.ingestion.batch;

import org.springframework.batch.core.SkipListener; 


public class IngestionSkipListener implements SkipListener<Object, Object> {

    @Override 
    public void onSkipInRead(Throwable t){
        // log skipped read 
    }
    
    @Override 
    public void onSkipInProcess(Object item, Throwable t){
        //Log skipped processing 
    }

    @Override 
    public void onSkipInWrite(Object item, Throwable t){
        //Log skipped write 
    }
}
