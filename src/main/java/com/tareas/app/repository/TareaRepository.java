package com.tareas.app.repository;

import com.tareas.app.model.Tarea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByUsuarioEmailOrderByFechaAsc(String email);

    Optional<Tarea> findByIdAndUsuarioEmail(Long id, String email);

    boolean existsByIdAndUsuarioEmail(Long id, String email);

    boolean existsByTipoTareaIdAndUsuarioEmail(Long tipoTareaId, String email);

    void deleteByIdAndUsuarioEmail(Long id, String email);
}