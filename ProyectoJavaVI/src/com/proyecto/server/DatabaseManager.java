package com.proyecto.server;

import com.proyecto.shared.Task;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

    public synchronized int autenticarUsuario(String usuario, String contrasena) {
        String query = "SELECT id_usuario FROM usuarios WHERE nombre_usuario = ? AND contrasena = ?";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
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

    public synchronized List<Task> obtenerTareas(int idUsuario) {
        List<Task> tareas = new ArrayList<>();
        String query = "SELECT t.*, u.nombre_usuario as owner_name FROM tareas t " +
                       "JOIN usuarios u ON t.owner_id = u.id_usuario " +
                       "WHERE t.owner_id = ? " +
                       "UNION " +
                       "SELECT t.*, u.nombre_usuario as owner_name FROM tareas t " +
                       "JOIN tareas_compartidas tc ON t.id_tarea = tc.tarea_id " +
                       "JOIN usuarios u ON t.owner_id = u.id_usuario " +
                       "WHERE tc.usuario_id = ?";
        
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idUsuario);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Task tarea = new Task();
                    tarea.setIdTarea(rs.getInt("id_tarea"));
                    tarea.setOwnerId(rs.getInt("owner_id"));
                    tarea.setTitulo(rs.getString("titulo"));
                    tarea.setDescripcion(rs.getString("descripcion"));
                    tarea.setEstado(rs.getString("estado"));
                    // Opcional: Podrías querer mostrar quién es el dueño.
                    // tarea.setOwnerName(rs.getString("owner_name")); 
                    tareas.add(tarea);
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al obtener tareas: " + e.getMessage());
        }
        return tareas;
    }

    public synchronized boolean crearTarea(int idUsuario, String titulo, String descripcion) {
        String query = "INSERT INTO tareas (owner_id, titulo, descripcion, estado) VALUES (?, ?, ?, 'PENDIENTE')";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
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
        String query = "SELECT t.owner_id, tc.permiso FROM tareas t " +
                       "LEFT JOIN tareas_compartidas tc ON t.id_tarea = tc.tarea_id AND tc.usuario_id = ? " +
                       "WHERE t.id_tarea = ?";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idTarea);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    boolean esOwner = rs.getInt("owner_id") == idUsuario;
                    String permiso = rs.getString("permiso");
                    boolean tienePermisoEdicion = "EDICION".equals(permiso);
                    return esOwner || tienePermisoEdicion;
                }
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al verificar permiso: " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean actualizarEstadoTarea(int idTarea, String nuevoEstado, int idUsuario) {
        if (!verificarPermisoEdicion(idTarea, idUsuario)) {
            registrarAuditoria(idUsuario, "ERROR_PERMISO", "Intento de actualizar tarea " + idTarea + " sin permiso.");
            return false;
        }
        String query = "UPDATE tareas SET estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_tarea = ?";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
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
        String checkOwnerQuery = "SELECT owner_id FROM tareas WHERE id_tarea = ?";
        try (PreparedStatement pstmt = conexion.prepareStatement(checkOwnerQuery)) {
            pstmt.setInt(1, idTarea);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getInt("owner_id") != idUsuario) {
                        registrarAuditoria(idUsuario, "ERROR_PERMISO", "Intento de eliminar tarea " + idTarea + " sin ser el dueño.");
                        return false; // No es el dueño, no puede eliminar
                    }
                } else {
                    return false; // Tarea no existe
                }
            }
        } catch (SQLException e) {
             System.out.println("[BD ERROR] Error al verificar dueño para eliminar: " + e.getMessage());
             return false;
        }

        String query = "DELETE FROM tareas WHERE id_tarea = ?";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
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

    private synchronized void registrarAuditoria(int idUsuario, String accion, String descripcion) {
        String query = "INSERT INTO auditoria (id_usuario, accion, descripcion) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
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
}
