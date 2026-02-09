package com.deepana.inventoryservice.dto.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReleaseInventoryCommand {

    private Long orderId;
    private String orderNumber;
    private String traceId;
}
