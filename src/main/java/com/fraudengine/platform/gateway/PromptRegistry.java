/**
 * ============================================================================
 * FILE:        src/main/java/com/fraudengine/platform/gateway/PromptRegistry.java
 * CONTEXT:     platform
 * LAYER:       infrastructure
 * PURPOSE:     Implement immutable prompt lookup with an explicit manifest and bounded version allowlist.
 * ----------------------------------------------------------------------------
 * OWNER:       Platform & Operations
 * SINCE:       week-26
 * RELATED:     ADR-001, ADR-002, ADR-004, curriculum week 30
 * ----------------------------------------------------------------------------
 * NOTES:
 *   - Every resource must match its published digest; unknown paths and changed content fail closed.
 *   - New releases add prompt versions rather than overwriting a version used by an existing investigation.
 * ============================================================================
 */
package com.fraudengine.platform.gateway;

import com.fraudengine.platform.api.PromptCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptRegistry implements PromptCatalog {
    private final String active;
    private final Map<String,String> manifest;
    public PromptRegistry(ObjectMapper mapper,@Value("${investigation.prompt-version:investigator-v1}")String active) {
        this.active=active;
        try(var stream=new ClassPathResource("prompts/manifest.json").getInputStream()) {
            var tree=mapper.readTree(stream);
            manifest=Map.copyOf(mapper.convertValue(tree.path("prompts"),new TypeReference<Map<String,String>>(){}));
        } catch(java.io.IOException error) { throw new IllegalStateException("Prompt manifest unavailable",error); }
        load(active);
    }
    @Override public Prompt active() { return load(active); }
    @Override public Prompt load(String version) {
        if(!manifest.containsKey(version)||!version.matches("investigator-v[1-9][0-9]*"))throw new IllegalArgumentException("Unknown prompt version");
        try(var stream=new ClassPathResource("prompts/"+version+".md").getInputStream()) {
            byte[] bytes=stream.readAllBytes();
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!digest.equals(manifest.get(version)))throw new IllegalStateException("Published prompt digest changed");
            String source=new String(bytes,StandardCharsets.UTF_8);
            return new Prompt(version,digest,source.substring(source.indexOf("-->")+3).strip());
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("Cannot load prompt",error); }
    }
    @Override public Prompt byReference(String reference) {
        String[] parts=reference.split("@",2);
        if(parts.length!=2)throw new IllegalArgumentException("Missing prompt digest");
        var prompt=load(parts[0]);
        if(!prompt.reference().equals(reference))throw new IllegalArgumentException("Prompt reference mismatch");
        return prompt;
    }
}
