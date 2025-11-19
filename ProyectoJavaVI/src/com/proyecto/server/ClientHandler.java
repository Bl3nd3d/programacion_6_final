package com.proyecto.server;

import com.proyecto.shared.Task;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

/**
 * ClientHandler: CLASE CRÍTICA PARA CONCURRENCIA
 * 
 * Cada vez que un cliente se conecta al servidor, se crea una instancia de esta clase
 * que corre en su PROPIO THREAD. Esto permite que el servidor maneje múltiples clientes
 * simultáneamente sin bloquear.
 * 
 * Diagrama de flujo:
 * 1. Cliente A se conecta → Servidor crea ClientHandler A en Thread A
 * 2. Cliente B se conecta → Servidor crea ClientHandler B en Thread B
 * 3. Ambos pueden enviar comandos simultáneamente sin interferencia
 * 4. DatabaseManager.synchronized previene conflictos en BD
 * 
 * IMPORTANTE: El acceso a BD está sincronizado en DatabaseManager, no aquí,
 * para evitar deadlocks.
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter salida;
    private Scanner entrada;
    private int idUsuario = -1;
    private String nombreUsuario;
    private static int contadorClientes = 0;
    
    public ClientHandler(Socket socket) {
        this.socket = socket;
        contadorClientes++;
    }
    
    /**
     * El método run() es lo que ejecuta el thread.
     * Se llama automáticamente cuando se hace start() en el thread.
     */
    @Override
    public void run() {
        String idThread = Thread.currentThread().getName();
        System.out.println("[SERVIDOR] " + idThread + " - Cliente conectado: " + socket.getInetAddress().getHostAddress());
        
        try {
            // Inicializar streams de entrada/salida
            salida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new Scanner(socket.getInputStream());
            
            // Mostrar menú de login
            mostrarMenuLogin();
            
            // Si no logró autenticarse, cierra la conexión
            if (idUsuario == -1) {
                System.out.println("[SERVIDOR] " + idThread + " - Cliente desconectado (autenticación fallida)");
                cerrarConexion();
                return;
            }
            
            // Menú principal de la aplicación
            boolean activo = true;
            while (activo && socket.isConnected()) {
                mostrarMenuPrincipal();
                
                if (!entrada.hasNextLine()) break;
                String opcion = entrada.nextLine().trim();
                
                switch (opcion) {
                    case "1":
                        listarTareas();
                        break;
                    case "2":
                        crearTarea();
                        break;
                    case "3":
                        actualizarEstadoTarea();
                        break;
                    case "4":
                        eliminarTarea();
                        break;
                    case "5":
                        activo = false;
                        salida.println("[SISTEMA] Sesión cerrada");
                        break;
                    default:
                        salida.println("[ERROR] Opción inválida");
                }
            }
            
        } catch (IOException e) {
            System.out.println("[SERVIDOR ERROR] " + Thread.currentThread().getName() + ": " + e.getMessage());
        } finally {
            cerrarConexion();
            contadorClientes--;
            System.out.println("[SERVIDOR] Clientes activos: " + contadorClientes);
        }
    }
    
    /**
     * Pantalla de login.
     * Aquí demostramos sincronización implícita a través de DatabaseManager.
     */
    private void mostrarMenuLogin() {
        salida.println("========================================");
        salida.println("     SISTEMA DE GESTIÓN DE TAREAS       ");
        salida.println("========================================");
        salida.println("Ingrese usuario:");
        
        if (!entrada.hasNextLine()) return;
        String usuario = entrada.nextLine().trim();
        
        salida.println("Ingrese contraseña:");
        if (!entrada.hasNextLine()) return;
        String contrasena = entrada.nextLine().trim();
        
        // AQUÍ OCURRE LA SINCRONIZACIÓN
        // Si dos clientes hacen login al mismo tiempo, DatabaseManager asegura
        // que las consultas se ejecuten secuencialmente, no en paralelo
        idUsuario = DatabaseManager.getInstance().autenticarUsuario(usuario, contrasena);
        
        if (idUsuario != -1) {
            nombreUsuario = usuario;
            salida.println("[ÉXITO] Bienvenido " + usuario + "!");
            salida.println("[INFO] Tu sesión está segura contra acceso concurrente.");
        } else {
            salida.println("[ERROR] Usuario o contraseña incorrectos");
        }
    }
    
    private void mostrarMenuPrincipal() {
        salida.println("\n========================================");
        salida.println("MENÚ PRINCIPAL - Usuario: " + nombreUsuario);
        salida.println("========================================");
        salida.println("1. Listar mis tareas");
        salida.println("2. Crear nueva tarea");
        salida.println("3. Actualizar estado de tarea");
        salida.println("4. Eliminar tarea");
        salida.println("5. Cerrar sesión");
        salida.println("----------------------------------------");
        salida.println("Seleccione opción:");
    }
    
    /**
     * Lista todas las tareas del usuario.
     * NOTA: Aunque se llama desde threads diferentes, DatabaseManager.synchronized
     * garantiza que las consultas SQL se ejecuten sin problemas.
     */
    private void listarTareas() {
        List<Task> tareas = DatabaseManager.getInstance().obtenerTareas(idUsuario);
        
        if (tareas.isEmpty()) {
            salida.println("[INFO] No tienes tareas creadas");
            return;
        }
        
        salida.println("\n========== TUS TAREAS ==========");
        for (Task tarea : tareas) {
            salida.println(String.format("ID: %d | %s | Estado: %s",
                    tarea.getIdTarea(), tarea.getTitulo(), tarea.getEstado()));
            salida.println("  Descripción: " + tarea.getDescripcion());
        }
        salida.println("================================\n");
    }
    
    /**
     * Crea una nueva tarea.
     * Aquí vemos cómo múltiples threads pueden llamar al mismo método simultáneamente
     * sin causar conflictos porque DatabaseManager sincroniza el acceso a BD.
     */
    private void crearTarea() {
        salida.println("Ingrese título de la tarea:");
        if (!entrada.hasNextLine()) return;
        String titulo = entrada.nextLine().trim();
        
        salida.println("Ingrese descripción:");
        if (!entrada.hasNextLine()) return;
        String descripcion = entrada.nextLine().trim();
        
        // Aquí ocurre acceso concurrente seguro
        boolean exitoso = DatabaseManager.getInstance().crearTarea(idUsuario, titulo, descripcion);
        
        if (exitoso) {
            salida.println("[ÉXITO] Tarea creada correctamente");
        } else {
            salida.println("[ERROR] No se pudo crear la tarea");
        }
    }
    
    /**
     * Actualiza el estado de una tarea.
     * CRÍTICO: Si dos threads intentan actualizar la MISMA tarea al mismo tiempo,
     * la sincronización en DatabaseManager previene condiciones de carrera.
     */
    private void actualizarEstadoTarea() {
        salida.println("Ingrese ID de la tarea a actualizar:");
        if (!entrada.hasNextLine()) return;
        
        try {
            int idTarea = Integer.parseInt(entrada.nextLine().trim());
            
            salida.println("Seleccione nuevo estado:");
            salida.println("1. PENDIENTE");
            salida.println("2. EN_PROGRESO");
            salida.println("3. COMPLETADA");
            
            if (!entrada.hasNextLine()) return;
            String opcionEstado = entrada.nextLine().trim();
            
            String nuevoEstado = switch (opcionEstado) {
                case "1" -> "PENDIENTE";
                case "2" -> "EN_PROGRESO";
                case "3" -> "COMPLETADA";
                default -> null;
            };
            
            if (nuevoEstado == null) {
                salida.println("[ERROR] Estado inválido");
                return;
            }
            
            // Acceso sincronizado a BD
            boolean exitoso = DatabaseManager.getInstance().actualizarEstadoTarea(idTarea, nuevoEstado, idUsuario);
            
            if (exitoso) {
                salida.println("[ÉXITO] Tarea actualizada a: " + nuevoEstado);
            } else {
                salida.println("[ERROR] No se pudo actualizar la tarea");
            }
        } catch (NumberFormatException e) {
            salida.println("[ERROR] ID de tarea inválido");
        }
    }
    
    /**
     * Elimina una tarea del usuario.
     */
    private void eliminarTarea() {
        salida.println("Ingrese ID de la tarea a eliminar:");
        if (!entrada.hasNextLine()) return;
        
        try {
            int idTarea = Integer.parseInt(entrada.nextLine().trim());
            
            boolean exitoso = DatabaseManager.getInstance().eliminarTarea(idTarea, idUsuario);
            
            if (exitoso) {
                salida.println("[ÉXITO] Tarea eliminada correctamente");
            } else {
                salida.println("[ERROR] No se pudo eliminar la tarea");
            }
        } catch (NumberFormatException e) {
            salida.println("[ERROR] ID de tarea inválido");
        }
    }
    
    /**
     * Cierra la conexión con el cliente.
     */
    private void cerrarConexion() {
        try {
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[SERVIDOR] " + Thread.currentThread().getName() + " - Conexión cerrada");
        } catch (IOException e) {
            System.out.println("[SERVIDOR ERROR] Error al cerrar: " + e.getMessage());
        }
    }
}
