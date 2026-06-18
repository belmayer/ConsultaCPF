package com.jucerja.consultaCpf.repository;
import com.jucerja.consultaCpf.entity.SituacaoCadastralCPF;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SituacaoCadastralCPFRespository extends JpaRepository<SituacaoCadastralCPF, Integer> {
    Optional<SituacaoCadastralCPF> findByCodigoSituacaoCPFCadastralRFB(String codigo);
}
