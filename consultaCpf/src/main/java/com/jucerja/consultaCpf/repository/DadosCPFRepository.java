package com.jucerja.consultaCpf.repository;

import com.jucerja.consultaCpf.entity.DadosCPF;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DadosCPFRepository extends JpaRepository<DadosCPF, Integer> {
    List<DadosCPF> findByConsultaS09IsNull();
}