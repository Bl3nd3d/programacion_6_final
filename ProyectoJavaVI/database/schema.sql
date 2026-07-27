-- Script de Base de Datos para Sistema de Gestión de Tareas
-- Sintaxis específica de MySQL (ENUM, AUTO_INCREMENT)

CREATE DATABASE IF NOT EXISTS task_manager;
USE task_manager;

-- Tabla de Usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario INT PRIMARY KEY AUTO_INCREMENT,
    nombre_usuario VARCHAR(50) UNIQUE NOT NULL,
    contrasena VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de Tareas
CREATE TABLE IF NOT EXISTS tareas (
    id_tarea INT PRIMARY KEY AUTO_INCREMENT,
    owner_id INT NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    estado ENUM('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA') DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_owner (owner_id),
    INDEX idx_estado (estado)
);

-- Tabla para manejar permisos de tareas compartidas
CREATE TABLE IF NOT EXISTS tareas_compartidas (
    id_compartido INT PRIMARY KEY AUTO_INCREMENT,
    tarea_id INT NOT NULL,
    usuario_id INT NOT NULL,
    permiso ENUM('LECTURA', 'EDICION') NOT NULL,
    FOREIGN KEY (tarea_id) REFERENCES tareas(id_tarea) ON DELETE CASCADE,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    UNIQUE KEY uq_tarea_usuario (tarea_id, usuario_id) -- Evita duplicados
);

-- Tabla de Auditoría (para registrar acciones concurrentes)
CREATE TABLE IF NOT EXISTS auditoria (
    id_auditoria INT PRIMARY KEY AUTO_INCREMENT,
    id_usuario INT,
    accion VARCHAR(100),
    descripcion TEXT,
    fecha_accion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE SET NULL
);

-- Inserts de datos de prueba
INSERT INTO usuarios (nombre_usuario, contrasena, email) VALUES
('admin', 'admin123', 'admin@empresa.com'),
('usuario1', 'pass123', 'usuario1@empresa.com'),
('usuario2', 'pass456', 'usuario2@empresa.com');

INSERT INTO tareas (owner_id, titulo, descripcion, estado) VALUES
(1, 'Configurar Servidor', 'Implementar servidor multihilo', 'EN_PROGRESO'),
(1, 'Pruebas de Carga', 'Realizar pruebas de concurrencia', 'PENDIENTE'),
(2, 'Documentación API', 'Escribir documentación completa', 'PENDIENTE');
