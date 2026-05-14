package com.jucerja.consultaCpf.controller;

import com.jucerja.consultaCpf.service.CpfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cpf")
public class CpfController {

    @Autowired
    private CpfService service;

    @GetMapping("/{cpf}")
    public String verificar(@PathVariable String cpf) {
        return service.verificarSituacao(cpf);
    }
}