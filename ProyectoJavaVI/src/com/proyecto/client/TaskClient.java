package com.proyecto.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * TaskClient: CLIENTE DE CONSOLA
 * 
 * Este es el lado del cliente de nuestra arquitectura cliente-servidor.
 * 
 * ARQUITECTURA CLIENTE-SERVIDOR:
 * ┌──────────────────┐                    ┌──────────────────┐
 * │   TaskClient     │                    │   TaskServer     │
 * │  (Este programa) │◄──────Socket──────►│  (El programa    │
 * │                  │                    │   en servidor.py)│
 * │ - Conecta al srv │                    │ - Escucha en 5555│
 * │ - Envía comandos │                    │ - Procesa datos  │
 * │ - Recibe datos   │                    │ - Accede BD      │
 * └──────────────────┘                    └──────────────────┘
 * 
 * El cliente:
 * 1. Se conecta al servidor en localhost:5555
 * 2. Envía usuario y contraseña
 * 3. Recibe confirmación de autenticación
 * 4. Entra en menú interactivo
 * 5. Envía comandos, recibe respuestas
 * 
 * Nota: En producción, usarías un cliente con GUI Swing o JavaFX,
 * pero para el examen una consola es suficiente y demostra los conceptos.
 */
public class TaskClient {
    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private final Scanner scannerLocal;
    
    /**
     * Constructor que inicializa el scanner local.
     */
    public TaskClient() {
        this.scannerLocal = new Scanner(System.in);
    }
    
    /**
     * Conecta al servidor remoto.
     */
    public boolean conectarAlServidor(String host, int puerto) {
        try {
            System.out.println("[CLIENTE] Intentando conectar a " + host + ":" + puerto + "...");
            socket = new Socket(host, puerto);
            
            // Crear streams de comunicación
            salida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            System.out.println("[CLIENTE] ✓ Conectado al servidor correctamente\n");
            return true;
            
        } catch (IOException e) {
            System.out.println("[CLIENTE ERROR] No se pudo conectar al servidor: " + e.getMessage());
            System.out.println("[CLIENTE] Verifica que el servidor esté ejecutándose en " + host + ":" + puerto);
            return false;
        }
    }
    
    /**
     * Inicia la interacción con el cliente.
     * 
     * El flujo es:
     * 1. Conectar al servidor
     * 2. Enviar credenciales (en loop que el servidor maneja)
     * 3. Recibir y mostrar respuestas del servidor
     * 4. Enviar comandos del usuario
     * 5. Recibir y mostrar resultados
     * 6. Cerrar conexión cuando usuario se desconecta
     */
    public void iniciar() {
        if (!conectarAlServidor("localhost", 5555)) {
            return;
        }
        
        try {
            String linea;
            
            // Loop principal de comunicación con servidor
            while (socket.isConnected()) {
                // Leer respuesta del servidor
                linea = entrada.readLine();
                
                if (linea == null) {
                    // El servidor cerró la conexión
                    System.out.println("[CLIENTE] Servidor cerró la conexión");
                    break;
                }
                
                // Mostrar respuesta del servidor
                System.out.println(linea);
                
                // Si el servidor pide entrada, leer del usuario local
                if (linea.trim().endsWith(":")) {
                    
                    // Leer entrada del usuario LOCAL
                    String entrada_usuario = scannerLocal.nextLine();
                    
                    // Enviar al SERVIDOR
                    salida.println(entrada_usuario);
                }
                
                // Si el usuario se desconectó, terminar
                if (linea.contains("Sesión cerrada")) {
                    break;
                }
            }
            
        } catch (IOException e) {
            System.out.println("[CLIENTE ERROR] Error de comunicación: " + e.getMessage());
        } finally {
            cerrarConexion();
        }
    }
    
    /**
     * Cierra la conexión con el servidor.
     */
    private void cerrarConexion() {
        try {
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[CLIENTE] Desconectado del servidor");
        } catch (IOException e) {
            System.out.println("[CLIENTE ERROR] Error al cerrar conexión: " + e.getMessage());
        }
    }
    
    /**
     * Punto de entrada del programa cliente.
     * Parámetros opcionales para conectar a diferentes servidores.
     */
    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println("        CLIENTE DE GESTIÓN DE TAREAS DISTRIBUIDAS      ");
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println("[INFO] Conectándose al servidor...");
        System.out.println("[INFO] Usuario: admin, Contraseña: admin123");
        System.out.println("[INFO] Usuario: usuario1, Contraseña: pass123");
        System.out.println("[INFO] Usuario: usuario2, Contraseña: pass456\n");
        
        TaskClient cliente = new TaskClient();
        cliente.iniciar();
    }
}

/**
 * PUNTOS CLAVE DEL CLIENTE:
 * 
 * 1. COMUNICACIÓN TCP/IP:
 *    - Socket: Representa la conexión con el servidor
 *    - PrintWriter: Envía datos al servidor
 *    - BufferedReader: Recibe datos del servidor
 * 
 * 2. PROTOCOLO CLIENTE-SERVIDOR:
 *    - Cliente envía: comandos, datos del usuario
 *    - Servidor procesa: lógica, acceso a BD
 *    - Servidor envía: menús, resultados, confirmaciones
 *    - Cliente recibe y muestra al usuario
 * 
 * 3. FLUJO SINCRÓNICO:
 *    - Cliente ESPERA respuesta del servidor antes de pedir siguiente entrada
 *    - Esto asegura que los datos lleguen en orden correcto
 *    - En sistemas reales, usarías callbacks o eventos asincronos
 * 
 * 4. MANEJO DE ERRORES:
 *    - Si servidor no está disponible: IOException al conectar
 *    - Si servidor cierra conexión: readline() retorna null
 *    - Si hay timeout de red: IOException
 */
