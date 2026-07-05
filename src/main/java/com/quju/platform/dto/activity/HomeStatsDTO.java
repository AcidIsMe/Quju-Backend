package com.quju.platform.dto.activity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeStatsDTO {
    private int featuredCount;
    private int availableSlots;
    private double avgRating;
}
