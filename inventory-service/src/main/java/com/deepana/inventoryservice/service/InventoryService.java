package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.dto.events.OrderCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;

public interface InventoryService {

    void processOrder(OrderCreatedEvent event) throws JsonProcessingException;
}
