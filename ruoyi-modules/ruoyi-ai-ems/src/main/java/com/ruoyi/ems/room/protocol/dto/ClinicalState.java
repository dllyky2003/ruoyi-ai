package com.ruoyi.ems.room.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalState {
    private String rhythm;         // svt / vf / asystole
    private String pulse;          // present / absent
    private String perfusion;      // stable / unstable
    private String consciousness;  // alert / altered / unresponsive
}
