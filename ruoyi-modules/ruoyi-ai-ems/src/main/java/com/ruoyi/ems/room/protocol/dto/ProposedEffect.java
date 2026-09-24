package com.ruoyi.ems.room.protocol.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProposedEffect {
    private String effectId;
    private boolean resolved;
}
