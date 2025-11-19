package com.proyecto.shared;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Clase que representa una Tarea en el sistema.
 * Implementa Serializable para poder ser enviada entre cliente y servidor.
 * Esta clase sirve como DTO (Data Transfer Object) para la comunicación.
 */
public class Task implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int idTarea;
    private int idUsuario;
    private String titulo;
    private String descripcion;
    private String estado; // PENDIENTE, EN_PROGRESO, COMPLETADA
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    
    // Constructores
    public Task() {}
    
    public Task(int idUsuario, String titulo, String descripcion) {
        this.idUsuario = idUsuario;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estado = "PENDIENTE";
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
    }
    
    // Getters y Setters
    public int getIdTarea() {
        return idTarea;
    }
    
    public void setIdTarea(int idTarea) {
        this.idTarea = idTarea;
    }
    
    public int getIdUsuario() {
        return idUsuario;
    }
    
    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }
    
    public String getTitulo() {
        return titulo;
    }
    
    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }
    
    public String getDescripcion() {
        return descripcion;
    }
    
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
    
    public String getEstado() {
        return estado;
    }
    
    public void setEstado(String estado) {
        this.estado = estado;
    }
    
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
    
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
    
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
    
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
    
    @Override
    public String toString() {
        return "Task{" +
                "idTarea=" + idTarea +
                ", titulo='" + titulo + '\'' +
                ", estado='" + estado + '\'' +
                ", fechaActualizacion=" + fechaActualizacion +
                '}';
    }
}
