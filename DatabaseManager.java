package com.proyecto.server;

import com.proyecto.shared.Task;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseManager: Gestiona todas las operaciones con la base de datos.
 * 
 * IMPORTANTE PARA CONCURRENCIA: Esta clase utiliza métodos synchronized para
 * garantizar que cuando múltiples threads (clientes) intenten acceder a la BD
 * simultáneamente, lo hagan de forma segura sin corrupción de datos.
 * 
 * El patrón Singleton asegura que toda la aplicación use una única conexión
 * compartida y controlada.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private Connection conexion;
    private static final String URL = "jdbc:mysql://localhost:3306/task_manager";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // Cambiar según tu configuración
    
    // Constructor privado para Singleton
    private DatabaseManager() {
        conectarBaseDatos();
    }
    
    // Método sincronizado para obtener la instancia (Thread-safe Singleton)
    public synchronized static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    /**
     * Conecta a la base de datos.
     * En un ambiente real, usarías un Connection Pool (HikariCP, etc)
     */
    private void conectarBaseDatos() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conexion = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("[BD] Conexión establecida correctamente");
        } catch (ClassNotFoundException e) {
            System.out.println("[BD ERROR] Driver MySQL no encontrado");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al conectar: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Autentica un usuario verificando credenciales en BD.
     * SYNCHRONIZED: Evita que múltiples threads hagan consultas de login simultáneamente
     */
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
        return -1; // Usuario no encontrado
    }
    
    /**
     * Obtiene todas las tareas de un usuario específico.
     * SYNCHRONIZED: Garantiza consistencia en lectura mientras otros threads escriben
     */
    public synchronized List<Task> obtenerTareas(int idUsuario) {
        List<Task> tareas = new ArrayList<>();
        String query = "SELECT * FROM tareas WHERE id_usuario = ? ORDER BY fecha_actualizacion DESC";
        
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Task tarea = new Task();
                    tarea.setIdTarea(rs.getInt("id_tarea"));
                    tarea.setIdUsuario(rs.getInt("id_usuario"));
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
    
    /**
     * Crea una nueva tarea en la base de datos.
     * SYNCHRONIZED: Múltiples usuarios pueden intentar crear tareas simultáneamente
     */
    public synchronized boolean crearTarea(int idUsuario, String titulo, String descripcion) {
        String query = "INSERT INTO tareas (id_usuario, titulo, descripcion, estado) VALUES (?, ?, ?, 'PENDIENTE')";
        
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setString(2, titulo);
            pstmt.setString(3, descripcion);
            
            int filasInsertadas = pstmt.executeUpdate();
            if (filasInsertadas > 0) {
                registrarAuditoria(idUsuario, "CREAR_TAREA", "Tarea creada: " + titulo);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al crear tarea: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Actualiza el estado de una tarea existente.
     * CRITICAL SYNCHRONIZED: Dos threads podrían intentar actualizar la misma tarea
     * al mismo tiempo. La sincronización previene condiciones de carrera (race conditions).
     */
    public synchronized boolean actualizarEstadoTarea(int idTarea, String nuevoEstado, int idUsuario) {
        String query = "UPDATE tareas SET estado = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE id_tarea = ? AND id_usuario = ?";
        
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setString(1, nuevoEstado);
            pstmt.setInt(2, idTarea);
            pstmt.setInt(3, idUsuario);
            
            int filasActualizadas = pstmt.executeUpdate();
            if (filasActualizadas > 0) {
                registrarAuditoria(idUsuario, "ACTUALIZAR_TAREA", "Tarea " + idTarea + " actualizada a " + nuevoEstado);
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al actualizar tarea: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Elimina una tarea del usuario.
     * SYNCHRONIZED: Previene que un thread intente eliminar mientras otro lee
     */
    public synchronized boolean eliminarTarea(int idTarea, int idUsuario) {
        String query = "DELETE FROM tareas WHERE id_tarea = ? AND id_usuario = ?";
        
        try (PreparedStatement pstmt = conexion.prepareStatement(query)) {
            pstmt.setInt(1, idTarea);
            pstmt.setInt(2, idUsuario);
            
            int filasEliminadas = pstmt.executeUpdate();
            if (filasEliminadas > 0) {
                registrarAuditoria(idUsuario, "ELIMINAR_TAREA", "Tarea " + idTarea + " eliminada");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("[BD ERROR] Error al eliminar tarea: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Registra acciones en la tabla de auditoría para seguimiento.
     * Útil para debugging de issues concurrentes.
     */
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
    
    /**
     * Cierra la conexión a la base de datos.
     * Se debe llamar al apagar el servidor.
     */
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
