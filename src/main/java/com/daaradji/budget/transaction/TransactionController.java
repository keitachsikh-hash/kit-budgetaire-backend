package com.daaradji.budget.transaction;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.config.CurrentUser;
import com.daaradji.budget.transaction.TransactionDtos.BatchRequest;
import com.daaradji.budget.transaction.TransactionDtos.PageResponse;
import com.daaradji.budget.transaction.TransactionDtos.SummaryResponse;
import com.daaradji.budget.transaction.TransactionDtos.TransactionFilter;
import com.daaradji.budget.transaction.TransactionDtos.TransactionRequest;
import com.daaradji.budget.transaction.TransactionDtos.TransactionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;
    private final CurrentUser currentUser;

    public TransactionController(TransactionService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<TransactionResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(currentUser.id(), new TransactionFilter(from, to, type, accountId, categoryId, q),
                page, size);
    }

    @GetMapping("/summary")
    public SummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q) {
        return service.summary(currentUser.id(), new TransactionFilter(from, to, type, accountId, categoryId, q));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q) {
        List<TransactionResponse> rows = service.export(currentUser.id(),
                new TransactionFilter(from, to, type, accountId, categoryId, q));
        StringBuilder csv = new StringBuilder("﻿Date;Type;Catégorie;Compte;Montant;Description\n");
        for (TransactionResponse t : rows) {
            csv.append(t.date()).append(';')
                    .append(t.type() == CategoryType.INCOME ? "Revenu" : "Dépense").append(';')
                    .append(escape(t.category().name())).append(';')
                    .append(escape(t.account().name())).append(';')
                    .append(t.amount().toPlainString()).append(';')
                    .append(escape(t.description())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"operations.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable Long id) {
        return service.get(currentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest req) {
        return service.create(currentUser.id(), req);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TransactionResponse> createAll(@Valid @RequestBody BatchRequest req) {
        return service.createAll(currentUser.id(), req.items());
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable Long id, @Valid @RequestBody TransactionRequest req) {
        return service.update(currentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        return v.contains(";") || v.contains("\n") || v.contains("\"") ? "\"" + v + "\"" : v;
    }
}
