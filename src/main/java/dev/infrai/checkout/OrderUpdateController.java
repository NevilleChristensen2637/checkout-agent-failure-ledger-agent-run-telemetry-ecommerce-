package dev.infrai.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
final class OrderUpdateController {
    private final CheckoutWorkflow workflow;
    OrderUpdateController(CheckoutWorkflow workflow) { this.workflow = workflow; }

    @PostMapping("/checkout")
    CheckoutWorkflow.OrderUpdate checkout(@RequestBody CheckoutWorkflow.Order order) {
        return workflow.process(order);
    }

    @GetMapping("/{orderId}")
    CheckoutWorkflow.OrderUpdate update(@PathVariable String orderId) {
        CheckoutWorkflow.OrderUpdate update = workflow.find(orderId);
        if (update == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order update not found");
        return update;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(InfraiFailureLedger.InfraiRejected.class)
    ResponseEntity<String> rejected(InfraiFailureLedger.InfraiRejected error) {
        int status = error.status >= 400 && error.status < 500 ? error.status : HttpStatus.BAD_GATEWAY.value();
        return ResponseEntity.status(status).body(error.getMessage());
    }
}
