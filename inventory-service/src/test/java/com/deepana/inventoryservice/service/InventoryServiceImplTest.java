package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.kafka.InventoryEventProducer;
import com.deepana.inventoryservice.repository.InventoryRepository;
import com.deepana.inventoryservice.repository.ProcessedInventoryEventRepository;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.deepana.saga.commondto.order.OrderItemEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private ProcessedInventoryEventRepository processedRepo;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryEventProducer producer;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private InventoryServiceImpl service;

    @Test
    void processReserve_emitsSingleReservedEvent_whenReservationSucceeds() {
        ReserveInventoryCommand cmd = new ReserveInventoryCommand();
        cmd.setSagaId("5c7742ed-218d-435d-95f0-210a4133f1d6");
        cmd.setOrderId(42L);
        cmd.setOrderNumber("ORD-1234");
        cmd.setTraceId("trace-1");
        cmd.setTotalAmount(new BigDecimal("19.99"));

        OrderItemEvent item = new OrderItemEvent();
        item.setProductId(99L);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("9.99"));
        cmd.setItems(List.of(item));

        Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setProductId(99L);
        inventory.setAvailableQty(5);
        inventory.setReservedQty(0);

        when(processedRepo.existsBySagaIdAndEventType(cmd.getSagaId(), "RESERVE")).thenReturn(false);
        when(processedRepo.existsBySagaIdAndEventType(cmd.getSagaId(), "RELEASED")).thenReturn(false);
        when(inventoryRepository.findByProductIdForUpdate(99L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(processedRepo.insertIfAbsent(cmd.getSagaId(), cmd.getOrderId(), "RESERVE")).thenReturn(1);

        service.processReserve(cmd);

        verify(producer, times(1)).sendInventoryReserved(any(InventoryReservedEvent.class));
        verify(producer, never()).sendInventoryFailed(any());
    }
}
