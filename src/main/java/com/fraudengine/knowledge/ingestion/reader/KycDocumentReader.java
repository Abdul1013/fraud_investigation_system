/**
 * KYC documents are C3 (Restricted). They are registered in the
 * document_registry for audit but are NEVER embedded or written to
 * the vector store. This reader returns an empty list for the
 * embedding pipeline and is handled separately by the registry writer.
 */

package com.fraudengine.knowledge.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;
import java.util.Map;

public class KycDocumentReader implements DocumentReader {
    
    private final String sourceId;
    private final int sourceVersion;

    public KycDocumentReader(String sourceId, int sourceVersion){

        this.sourceId = sourceId;
        this.sourceVersion = sourceVersion;
    }

    @Override 
    public List<Document> get(){
        // C3 documents are not embedded. Return an empty list so the
        // ETL pipeline skips them. Registration happens in the
        // DocumentRegistryWriter.

        return List.of();

    }
    
}
