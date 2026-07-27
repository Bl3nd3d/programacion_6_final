package com.proyecto.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.shared.Task;

public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection conexion;
    private static final String URL = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/task_manager");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    private DatabaseManager() {
        conectarBaseDatos();
    }

    public synchronized static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void conectarBaseDatos() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conexion = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("[BD] Conexión establecida correctamente");
        } catch (ClassNotFoundException e) {
            System.out.println("[BD ERROR] Driver MySQL no encontrado");
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al conectar: " + e.getMessage());
        }
    }

    // NUEVO MÉTODO: Verifica si la conexión está viva. Si no, se reconecta.
    private Connection getConexionActiva() throws SQLException {
        // Si es nula, está cerrada, o no responde en 2 segundos, reconectamos.
        if (conexion == null || conexion.isClosed() || !conexion.isValid(2)) {
            System.out.println("[BD] Conexión perdida. Intentando reconectar...");
            conectarBaseDatos();
        }
        return conexion;
    }

    public synchronized int autenticarUsuario(String usuario, String contrasena) {
        String query = "SELECT id_usuario FROM usuarios WHERE nombre_usuario = ? AND contrasena = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setString(1, usuario);
            pstmt.setString(2, contrasena);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int idUsuario = rs.getInt("id_usuario");
                    registrarAuditoria(idUsuario, "LOGIN", "Usuario " + usuario + " inició sesión");
                    return idUsuario;
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error en autenticación: " + e.getMessage());
        }
        return -1;
    }

    public synchronized String getUsername(int idUsuario) {
        String query = "SELECT nombre_usuario FROM usuarios WHERE id_usuario = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("nombre_usuario");
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al obtener nombre de usuario: " + e.getMessage());
        }
        return null;
    }

    public synchronized List<Task> obtenerTareas(int idUsuario) {
        List<Task> tareas = new ArrayList<>();
        String query = "SELECT t.*, u.nombre_usuario as owner_name FROM tareas t " +
                       "JOIN usuarios u ON t.id_usuario = u.id_usuario " +
                       "WHERE t.id_usuario = ? " +
                       "UNION " +
                       "SELECT t.*, u.nombre_usuario as owner_name FROM tareas t " +
                       "JOIN tareas_compartidas tc ON t.id_tarea = tc.id_tarea " +
                       "JOIN usuarios u ON t.id_usuario = u.id_usuario " +
                       "WHERE tc.id_usuario = ?";
        
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idUsuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Task tarea = new Task();
                    tarea.setIdTarea(rs.getInt("id_tarea"));
                    tarea.setOwnerId(rs.getInt("id_usuario"));
                    tarea.setTitulo(rs.getString("titulo"));
                    tarea.setDescripcion(rs.getString("descripcion"));
                    tarea.setEstado(rs.getString("estado"));
                    tareas.add(tarea);
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al obtener tareas: " + e.getMessage());
        }
        return tareas;
    }

    public synchronized boolean crearTarea(int idUsuario, String titulo, String descripcion) {
        String query = "INSERT INTO tareas (id_usuario, titulo, descripcion, estado) VALUES (?, ?, ?, 'PENDIENTE')";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setString(2, titulo);
            pstmt.setString(3, descripcion);
            if (pstmt.executeUpdate() > 0) {
                registrarAuditoria(idUsuario, "CREAR_TAREA", "Tarea creada: " + titulo);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al crear tarea: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean verificarPermisoEdicion(int idTarea, int idUsuario) {
        boolean tienePermiso = false;
        
        // Consulta SQL que verifica ambas condiciones
        String sql = "SELECT 1 FROM tareas WHERE id_tarea = ? AND id_usuario = ? " +
                     "UNION " +
                     "SELECT 1 FROM tareas_compartidas WHERE id_tarea = ? AND id_usuario = ? AND permiso = 'EDICION'";

        try (Connection conn = getConexionActiva();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, idTarea);
            pstmt.setInt(2, idUsuario);
            pstmt.setInt(3, idTarea);
            pstmt.setInt(4, idUsuario);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    tienePermiso = true; // Si la consulta devuelve al menos una fila, tiene permiso
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error verificando permisos: " + e.getMessage());
        }
        
        return tienePermiso;
    }

    public synchronized boolean actualizarEstadoTarea(int idTarea, String nuevoEstado, int idUsuario) {
        if (!verificarPermisoEdicion(idTarea, idUsuario)) {
            registrarAuditoria(idUsuario, "ERROR_PERMISO", "Intento de actualizar tarea " + idTarea + " sin permiso.");
            return false;
        }
        String query = "UPDATE tareas SET estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_tarea = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setString(1, nuevoEstado);
            pstmt.setInt(2, idTarea);
            if (pstmt.executeUpdate() > 0) {
                registrarAuditoria(idUsuario, "ACTUALIZAR_TAREA", "Tarea " + idTarea + " actualizada a " + nuevoEstado);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al actualizar tarea: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean eliminarTarea(int idTarea, int idUsuario) {
        String checkOwnerQuery = "SELECT id_usuario FROM tareas WHERE id_tarea = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(checkOwnerQuery)) {
            pstmt.setInt(1, idTarea);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt("id_usuario") != idUsuario) {
                        registrarAuditoria(idUsuario, "ERROR_PERMISO", "Intento de eliminar tarea " + idTarea + " sin ser el dueño.");
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } catch (SQLException e) {
             System.out.println("[BD ERROR] Error al verificar dueño para eliminar: " + e.getMessage());
             return false;
        }

        String query = "DELETE FROM tareas WHERE id_tarea = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idTarea);
            if (pstmt.executeUpdate() > 0) {
                registrarAuditoria(idUsuario, "ELIMINAR_TAREA", "Tarea " + idTarea + " eliminada");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al eliminar tarea: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean asignarTareaUsuario(int idTarea, int idUsuarioDestino, int idOwner) {
        // Verificar que el que asigna es realmente el dueño de la tarea
        String checkOwner = "SELECT 1 FROM tareas WHERE id_tarea = ? AND id_usuario = ?";
        try (PreparedStatement pstCheck = getConexionActiva().prepareStatement(checkOwner)) {
            pstCheck.setInt(1, idTarea);
            pstCheck.setInt(2, idOwner);
            try (ResultSet rs = pstCheck.executeQuery()) {
                if (!rs.next()) {
                    registrarAuditoria(idOwner, "ERROR_ASIGNACION", "Intento de asignar tarea " + idTarea + " sin ser dueño.");
                    return false; // No es el creador/admin de esta tarea
                }
            }
        } catch(SQLException e) { return false; }

        // Si es el dueño, se la asigna al usuario destino con INSERT IGNORE para no duplicar
        String query = "INSERT IGNORE INTO tareas_compartidas (id_tarea, id_usuario, permiso) VALUES (?, ?, 'EDICION')";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idTarea);
            pstmt.setInt(2, idUsuarioDestino);
            if(pstmt.executeUpdate() > 0) {
                registrarAuditoria(idOwner, "ASIGNAR_TAREA", "Tarea " + idTarea + " asignada al usuario " + idUsuarioDestino);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al asignar tarea: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean actualizarTarea(int idTarea, int idUsuario, String titulo, String descripcion, String estado) {
        if (!verificarPermisoEdicion(idTarea, idUsuario)) {
            registrarAuditoria(idUsuario, "ERROR_PERMISO", "Intento de actualizar tarea " + idTarea + " sin permiso.");
            return false;
        }
        String query = "UPDATE tareas SET titulo = ?, descripcion = ?, estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_tarea = ?";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setString(1, titulo);
            pstmt.setString(2, descripcion);
            pstmt.setString(3, estado);
            pstmt.setInt(4, idTarea);
            if (pstmt.executeUpdate() > 0) {
                registrarAuditoria(idUsuario, "ACTUALIZAR_TAREA_COMPLETA", "Tarea " + idTarea + " actualizada.");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al actualizar tarea: " + e.getMessage());
        }
        return false;
    }

    public synchronized void registrarAuditoria(int idUsuario, String accion, String descripcion) {
        String query = "INSERT INTO auditoria (id_usuario, accion, descripcion) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setString(2, accion);
            pstmt.setString(3, descripcion);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al registrar auditoría: " + e.getMessage());
        }
    }

    public synchronized void cerrarConexion() {
        try {
            if (conexion != null && !conexion.isClosed()) {
                conexion.close();
                System.out.println("[BD] Conexión cerrada");
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al cerrar conexión: " + e.getMessage());
        }
    }

    // 1. Método para obtener todos los usuarios y mostrarlos en el frontend
    public synchronized String obtenerUsuariosJson() {
        StringBuilder sb = new StringBuilder("[");
        String query = "SELECT id_usuario, nombre_usuario FROM usuarios";
        try (PreparedStatement pstmt = getConexionActiva().prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            boolean primero = true;
            while (rs.next()) {
                if (!primero) sb.append(",");
                sb.append("{\"idUsuario\":").append(rs.getInt("id_usuario"))
                  .append(",\"nombreUsuario\":\"").append(escaparJson(rs.getString("nombre_usuario"))).append("\"}");
                primero = false;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al obtener usuarios: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // Método auxiliar interno para el JSON de usuarios
    private String escaparJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}