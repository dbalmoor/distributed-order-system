package com.deepana.inventoryservice.service;

import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface InventoryService {

    void processReserve(ReserveInventoryCommand command)
            throws JsonProcessingException;
}
