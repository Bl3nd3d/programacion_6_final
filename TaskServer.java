package com.proyecto.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * TaskServer: SERVIDOR MULTIHILO
 * 
 * Este es el corazón del sistema cliente-servidor. El servidor:
 * 
 * 1. Crea un ServerSocket que escucha en un puerto específico (5555)
 * 2. Entra en un loop infinito esperando conexiones de clientes
 * 3. Cuando un cliente se conecta, crea una NUEVA instancia de ClientHandler
 * 4. Asigna esa instancia a un NUEVO THREAD usando Thread class
 * 5. El servidor continúa escuchando mientras el thread maneja al cliente
 * 
 * MODELO DE CONCURRENCIA:
 * ┌─────────────────┐
 * │   TaskServer    │  (Corre en el thread MAIN)
 * │  (Escuchando)   │
 * └────────┬────────┘
 *          │ new Socket()
 *          ├─────────────────────┬─────────────┬──────────
 *          │                     │             │
 *    ┌─────▼──────┐      ┌──────▼────┐  ┌───▼──────┐
 *    │ Cliente A   │      │ Cliente B  │  │Cliente C │
 *    │ (Thread 1)  │      │ (Thread 2) │  │(Thread 3)│
 *    └─────────────┘      └───────────┘  └──────────┘
 * 
 * Los Threads se comunican con BD a través de DatabaseManager sincronizado.
 */
public class TaskServer {
    private static final int PUERTO = 5555;
    private static final int MAX_CLIENTES = 50;
    private ServerSocket serverSocket;
    private List<Thread> clientesActivos;
    private boolean ejecutando = true;
    
    public TaskServer() {
        this.clientesActivos = new ArrayList<>();
    }
    
    /**
     * Inicia el servidor y comienza a escuchar conexiones.
     */
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(PUERTO);
            System.out.println("═══════════════════════════════════════════════");
            System.out.println("      SERVIDOR DE GESTIÓN DE TAREAS INICIADO");
            System.out.println("═══════════════════════════════════════════════");
            System.out.println("[SERVIDOR] Escuchando en puerto: " + PUERTO);
            System.out.println("[SERVIDOR] Máximo de clientes: " + MAX_CLIENTES);
            System.out.println("[SERVIDOR] Base de datos: task_manager");
            System.out.println("═══════════════════════════════════════════════\n");
            
            // Loop principal del servidor
            while (ejecutando) {
                try {
                    // Espera a que un cliente se conecte (BLOQUEANTE)
                    // Si no hay clientes, el servidor permanece aquí
                    Socket socketCliente = serverSocket.accept();
                    
                    // Verifica límite de clientes
                    if (clientesActivos.size() >= MAX_CLIENTES) {
                        System.out.println("[SERVIDOR] Límite de clientes alcanzado, rechazando conexión");
                        socketCliente.close();
                        continue;
                    }
                    
                    // MOMENTO CRÍTICO: Crear thread para el cliente
                    ClientHandler manejador = new ClientHandler(socketCliente);
                    
                    // Crear un nuevo thread que correrá el método run() de ClientHandler
                    Thread threadCliente = new Thread(
                        manejador, 
                        "Cliente-" + (clientesActivos.size() + 1)
                    );
                    
                    // Iniciar el thread
                    threadCliente.start();
                    
                    // Guardar referencia
                    clientesActivos.add(threadCliente);
                    
                    System.out.println("[SERVIDOR] Threads activos: " + 
                        Thread.activeCount() + " | Clientes: " + clientesActivos.size());
                    
                } catch (IOException e) {
                    System.out.println("[SERVIDOR ERROR] Error aceptando conexión: " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            System.out.println("[SERVIDOR ERROR] Error al iniciar servidor: " + e.getMessage());
        } finally {
            detener();
        }
    }
    
    /**
     * Detiene el servidor de forma segura.
     * NOTA: En un ambiente real, usarías ExecutorService para mejor manejo de threads.
     */
    public void detener() {
        ejecutando = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("[SERVIDOR] ServerSocket cerrado");
            }
        } catch (IOException e) {
            System.out.println("[SERVIDOR ERROR] Error al cerrar servidor: " + e.getMessage());
        }
        
        // Espera a que todos los threads terminen
        for (Thread thread : clientesActivos) {
            try {
                thread.join(5000); // Espera máximo 5 segundos
            } catch (InterruptedException e) {
                System.out.println("[SERVIDOR] Error esperando thread: " + e.getMessage());
            }
        }
        
        // Cierra conexión a BD
        DatabaseManager.getInstance().cerrarConexion();
        
        System.out.println("[SERVIDOR] Servidor detenido correctamente");
    }
    
    /**
     * Punto de entrada de la aplicación servidor.
     */
    public static void main(String[] args) {
        TaskServer servidor = new TaskServer();
        
        // Agregar hook para apagar gracefully con Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVIDOR] Recibida señal de apagado...");
            servidor.detener();
        }));
        
        // Iniciar el servidor
        servidor.iniciar();
    }
}

/**
 * EXPLICACIÓN DE CONCURRENCIA EN ESTE CÓDIGO:
 * 
 * 1. MULTITHREADING:
 *    - El thread MAIN ejecuta TaskServer.iniciar()
 *    - Cada cliente que se conecta obtiene su PROPIO thread
 *    - Los threads corren EN PARALELO en sistemas multi-core
 * 
 * 2. SINCRONIZACIÓN:
 *    - DatabaseManager.getInstance() usa SYNCHRONIZED en todos los métodos
 *    - Esto evita que dos threads modifiquen la BD al mismo tiempo
 *    - Ejemplo: Usuario A y Usuario B intentan crear tareas a la vez
 *      → Thread A llama crearTarea()
 *      → Thread B intenta llamar crearTarea() → ESPERA
 *      → Thread A termina → Thread B procede
 * 
 * 3. RACE CONDITIONS PREVENIDAS:
 *    - Sin sincronización: Dos threads podrían leer el mismo contador
 *      y ambos incrementar al mismo tiempo, resultando en valor incorrecto
 *    - Con sincronización: Un thread obtiene el lock, lee, modifica, escribe,
 *      libera el lock. El otro thread espera.
 * 
 * 4. DEADLOCK PREVENTION:
 *    - No hay locks anidados (todos los locks están en DatabaseManager)
 *    - ClientHandler NO lockea nada, solo usa DatabaseManager
 *    - Esto previene deadlocks donde Thread A espera Thread B y viceversa
 * 
 * 5. VENTAJA DEL DISEÑO:
 *    - 100 clientes conectados = 100 threads simultáneos
 *    - Cada uno puede ejecutar su propia lógica
 *    - Solo sincronizan cuando acceden a BD (punto de contención)
 */
