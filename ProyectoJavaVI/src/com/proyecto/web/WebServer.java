package com.proyecto.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.proyecto.server.DatabaseManager;
import com.proyecto.server.TaskServer;
import com.proyecto.shared.Task;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    private static final int PUERTO_WEB = 8080;
    private static final ConcurrentHashMap<String, Integer> sesionesActivas = new ConcurrentHashMap<>();

    public static void iniciar() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(PUERTO_WEB), 0);
        servidor.createContext("/", WebServer::manejarIndex);
        servidor.createContext("/api/login", WebServer::manejarLogin);
        servidor.createContext("/api/tareas", WebServer::manejarTareas);
        servidor.createContext("/api/lock", WebServer::manejarLock);
        servidor.createContext("/api/session", WebServer::manejarSession);
        servidor.createContext("/api/usuarios", WebServer::manejarUsuarios);
        servidor.createContext("/api/asignar", WebServer::manejarAsignar);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
        System.out.println("[WEB] Interfaz web disponible en http://localhost:" + PUERTO_WEB);
    }

    private static void manejarIndex(HttpExchange exchange) throws IOException {
        System.out.println(
            "[WEB] Nuevo usuario conectado desde la IP: " + exchange.getRemoteAddress().getAddress().getHostAddress()
        );
        responder(exchange, 200, "text/html; charset=utf-8", PAGINA_HTML);
    }

    private static void manejarLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            responder(exchange, 405, "text/plain", "Método no permitido");
            return;
        }
        Map<String, String> datos = leerCuerpo(exchange);
        String usuario = datos.getOrDefault("usuario", "");
        String contrasena = datos.getOrDefault("contrasena", "");

        int idUsuario = DatabaseManager.getInstance().autenticarUsuario(usuario, contrasena);

        if (idUsuario != -1) {
            String token = UUID.randomUUID().toString();
            sesionesActivas.put(token, idUsuario);
            responder(exchange, 200, "application/json",
                    "{\"ok\":true,\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + escaparJson(usuario) + "\",\"token\":\"" + token + "\"}");
        } else {
            responder(exchange, 200, "application/json", "{\"ok\":false}");
        }
    }

    private static void manejarTareas(HttpExchange exchange) throws IOException {
        DatabaseManager bd = DatabaseManager.getInstance();

        switch (exchange.getRequestMethod()) {
            case "GET" -> {
                Map<String, String> parametros = parsear(exchange.getRequestURI().getRawQuery());
                int idUsuario = parseIntSeguro(parametros.get("idUsuario"));
                List<Task> tareas = bd.obtenerTareas(idUsuario);
                responder(exchange, 200, "application/json", tareasAJson(tareas));
            }
            case "POST" -> {
                Map<String, String> datos = leerCuerpo(exchange);
                int idUsuario = parseIntSeguro(datos.get("idUsuario"));
                boolean ok = bd.crearTarea(idUsuario, datos.getOrDefault("titulo", ""), datos.getOrDefault("descripcion", ""));
                responder(exchange, 200, "application/json", "{\"ok\":" + ok + "}");
            }
            case "PUT" -> {
                Map<String, String> datos = leerCuerpo(exchange);
                int idUsuario = parseIntSeguro(datos.get("idUsuario"));
                int idTarea = parseIntSeguro(datos.get("idTarea"));
                String estado = datos.getOrDefault("estado", "");
                
                boolean ok = bd.actualizarEstadoTarea(idTarea, estado, idUsuario);
                responder(exchange, 200, "application/json", "{\"ok\":" + ok + "}");
            }
            case "DELETE" -> {
                Map<String, String> datos = leerCuerpo(exchange);
                int idUsuario = parseIntSeguro(datos.get("idUsuario"));
                int idTarea = parseIntSeguro(datos.get("idTarea"));
                boolean ok = bd.eliminarTarea(idTarea, idUsuario);
                responder(exchange, 200, "application/json", "{\"ok\":" + ok + "}");
            }
            default -> responder(exchange, 405, "text/plain", "Método no permitido");
        }
    }

    private static void manejarLock(HttpExchange exchange) throws IOException {
        Map<String, String> datos;
        if ("POST".equals(exchange.getRequestMethod())) {
            datos = leerCuerpo(exchange);
        } else if ("DELETE".equals(exchange.getRequestMethod())) {
            datos = parsear(exchange.getRequestURI().getRawQuery());
        } else {
            responder(exchange, 405, "text/plain", "Método no permitido");
            return;
        }

        int idTarea = parseIntSeguro(datos.get("idTarea"));
        int idUsuario = parseIntSeguro(datos.get("idUsuario"));
        String nombreUsuario = datos.get("nombreUsuario");

        if (idTarea == -1 || idUsuario == -1 || nombreUsuario == null || nombreUsuario.isBlank()) {
            responder(exchange, 400, "application/json", "{\"ok\":false, \"message\":\"Bad Request\"}");
            return;
        }

        switch (exchange.getRequestMethod()) {
            case "POST": { 
                if (!DatabaseManager.getInstance().verificarPermisoEdicion(idTarea, idUsuario)) {
                    responder(exchange, 403, "application/json", "{\"ok\":false, \"message\":\"Forbidden\"}");
                    return;
                }

                if (TaskServer.bloquearTarea(idTarea, nombreUsuario)) {
                    responder(exchange, 200, "application/json", "{\"ok\":true}");
                } else {
                    responder(exchange, 409, "application/json", "{\"ok\":false, \"message\":\"Conflict\"}");
                }
                break;
            }
            case "DELETE": { 
                TaskServer.liberarTarea(idTarea, nombreUsuario);
                responder(exchange, 200, "application/json", "{\"ok\":true}");
                break;
            }
        }
    }

    private static void manejarSession(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "GET" -> {
                String token = parsear(exchange.getRequestURI().getRawQuery()).get("token");
                if (token == null) {
                    responder(exchange, 400, "application/json", "{\"ok\":false, \"message\":\"Token required\"}");
                    return;
                }

                Integer idUsuario = sesionesActivas.get(token);
                if (idUsuario != null) {
                    String nombreUsuario = DatabaseManager.getInstance().getUsername(idUsuario);
                    if (nombreUsuario != null) {
                        responder(exchange, 200, "application/json",
                                "{\"ok\":true,\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + escaparJson(nombreUsuario) + "\"}");
                    } else {
                         responder(exchange, 200, "application/json", "{\"ok\":false, \"message\":\"User not found\"}");
                    }
                } else {
                    responder(exchange, 200, "application/json", "{\"ok\":false, \"message\":\"Invalid or expired token\"}");
                }
            }
            case "DELETE" -> {
                String token = parsear(exchange.getRequestURI().getRawQuery()).get("token");
                if (token != null) {
                    sesionesActivas.remove(token);
                    responder(exchange, 200, "application/json", "{\"ok\":true, \"message\":\"Session ended\"}");
                } else {
                    responder(exchange, 400, "application/json", "{\"ok\":false, \"message\":\"Token required\"}");
                }
            }
            default -> responder(exchange, 405, "text/plain", "Método no permitido");
        }
    }

    private static void manejarUsuarios(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            String json = DatabaseManager.getInstance().obtenerUsuariosJson();
            responder(exchange, 200, "application/json", json);
        } else {
            responder(exchange, 405, "text/plain", "Método no permitido");
        }
    }

    private static void manejarAsignar(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            Map<String, String> datos = leerCuerpo(exchange);
            int idOwner = parseIntSeguro(datos.get("idUsuario")); // El que hace la petición
            int idTarea = parseIntSeguro(datos.get("idTarea"));
            int idUsuarioDestino = parseIntSeguro(datos.get("idUsuarioDestino"));

            boolean ok = DatabaseManager.getInstance().asignarTareaUsuario(idTarea, idUsuarioDestino, idOwner);
            if (ok) {
                responder(exchange, 200, "application/json", "{\"ok\":true}");
            } else {
                responder(exchange, 403, "application/json", "{\"ok\":false, \"message\":\"No eres el dueño o el usuario ya está asignado.\"}");
            }
        }
    }

    private static Map<String, String> leerCuerpo(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String cuerpo = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return parsear(cuerpo);
    }

    private static Map<String, String> parsear(String query) {
        Map<String, String> resultado = new HashMap<>();
        if (query == null || query.isBlank()) return resultado;
        for (String par : query.split("&")) {
            String[] partes = par.split("=", 2);
            String clave = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            String valor = partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
            resultado.put(clave, valor);
        }
        return resultado;
    }

    private static int parseIntSeguro(String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String tareasAJson(List<Task> tareas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tareas.size(); i++) {
            Task t = tareas.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"idTarea\":").append(t.getIdTarea())
              .append(",\"titulo\":\"").append(escaparJson(t.getTitulo())).append("\"")
              .append(",\"descripcion\":\"").append(escaparJson(t.getDescripcion())).append("\"")
              .append(",\"estado\":\"").append(escaparJson(t.getEstado())).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escaparJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static void responder(HttpExchange exchange, int codigo, String tipoContenido, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", tipoContenido);
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final String PAGINA_HTML = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Gestión de Tareas</title>
        <style>
          :root { --azul:#2563eb; --gris:#f4f5f7; --texto:#1f2937; --borde:#e5e7eb; }
          * { box-sizing: border-box; }
          body { font-family: system-ui, sans-serif; background: var(--gris); color: var(--texto); margin:0; }
          .contenedor { max-width: 640px; margin: 0 auto; padding: 32px 16px; }
          h1 { font-size: 1.4rem; margin-bottom: 4px; }
          .subtitulo { color:#6b7280; margin-bottom: 24px; font-size:.85rem; }
          .tarjeta { background:#fff; border:1px solid var(--borde); border-radius:10px; padding:20px; margin-bottom:16px; }
          label { display:block; font-size:.85rem; margin-bottom:4px; color:#374151; font-weight: bold;}
          input, select, textarea { width:100%; padding:8px 10px; border:1px solid var(--borde); border-radius:6px; margin-bottom:12px; font-size:.95rem; font-family: inherit; }
          button { background: var(--azul); color:#fff; border:0; padding:9px 16px; border-radius:6px; cursor:pointer; font-size:.9rem; }
          button.secundario { background:#e5e7eb; color:#374151; }
          button.peligro { background:#dc2626; }
          .fila { display:flex; gap:8px; align-items:center; }
          .tarea { border:1px solid var(--borde); border-radius:8px; padding:12px 14px; margin-bottom:10px; }
          .tarea-header { display:flex; justify-content:space-between; align-items:center; }
          .estado { font-size:.72rem; padding:2px 8px; border-radius:999px; font-weight:600; white-space:nowrap; }
          .PENDIENTE { background:#fef3c7; color:#92400e; }
          .EN_PROGRESO { background:#dbeafe; color:#1e40af; }
          .COMPLETADA { background:#dcfce7; color:#166534; }
          .oculto { display:none; }
          #error-login { color:#dc2626; font-size:.85rem; margin-top:-6px; }
          .modal { position: fixed; z-index: 10; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.4); }
          .modal-contenido { background: #fff; margin: 10vh auto; padding: 24px; border-radius: 10px; max-width: 500px; width: 90%; }
        </style>
        </head>
        <body>
        <div class="contenedor">
          <h1>Gestión de Tareas</h1>
          <div class="subtitulo">Panel de Control de Tareas</div>

          <div id="vista-login" class="tarjeta">
            <label>Usuario</label>
            <input id="usuario" autocomplete="username">
            <label>Contraseña</label>
            <input id="contrasena" type="password" autocomplete="current-password">
            <div id="error-login" class="oculto">Usuario o contraseña incorrectos</div>
            <button onclick="iniciarSesion()">Ingresar</button>
          </div>

          <div id="vista-app" class="oculto">
            <div class="tarjeta fila" style="justify-content:space-between;">
              <div>Conectado como <strong id="nombre-usuario"></strong></div>
              <button class="secundario" onclick="cerrarSesion()">Cerrar sesión</button>
            </div>

            <div class="tarjeta">
              <label>Título de la Tarea</label>
              <input id="nueva-titulo">
              <label>Descripción</label>
              <textarea id="nueva-descripcion" rows="2"></textarea>
              <button onclick="crearTarea()">Crear tarea</button>
            </div>

            <div id="lista-tareas"></div>
          </div>
        </div>

        <div id="modal-edicion" class="modal oculto">
          <div class="modal-contenido">
            <h2>Editando Tarea</h2>
            <input type="hidden" id="edit-id-tarea">
            
            <label>Título (Solo Lectura)</label>
            <input id="edit-titulo" disabled>
            
            <label>Descripción (Solo Lectura)</label>
            <textarea id="edit-descripcion" rows="3" disabled></textarea>
            
            <label>Actualizar Estado</label>
            <select id="edit-estado">
              <option value="PENDIENTE">PENDIENTE</option>
              <option value="EN_PROGRESO">EN_PROGRESO</option>
              <option value="COMPLETADA">COMPLETADA</option>
            </select>

            <hr style="margin: 20px 0; border: 0; border-top: 1px solid var(--borde);">
            
            <!-- SECCIÓN NUEVA DE ASIGNACIÓN -->
            <label>Asignar tarea a un colaborador (Solo el creador puede asignar)</label>
            <div class="fila">
                <select id="select-usuarios">
                    <option value="">Seleccione un usuario...</option>
                </select>
                <button class="secundario" onclick="asignarTarea()" style="white-space: nowrap;">Asignar</button>
            </div>

            <div class="fila" style="justify-content: flex-end; margin-top: 20px;">
              <button class="secundario" onclick="cancelarEdicion()">Cerrar / Cancelar</button>
              <button onclick="guardarCambios()">Guardar Estado</button>
            </div>
          </div>
        </div>

        <script>
        let idUsuario = null;
        let nombreUsuario = null;

        document.addEventListener('DOMContentLoaded', async () => {
            const token = localStorage.getItem('mi_token');
            if (token) {
                const resp = await fetch('/api/session?token=' + token);
                const datos = await resp.json();
                if (datos.ok) {
                    idUsuario = datos.idUsuario;
                    nombreUsuario = datos.nombre;
                    document.getElementById('nombre-usuario').textContent = nombreUsuario;
                    document.getElementById('vista-login').classList.add('oculto');
                    document.getElementById('vista-app').classList.remove('oculto');
                    document.getElementById('error-login').classList.add('oculto');
                    cargarTareas();
                    cargarListaUsuarios(); // Cargar la lista al verificar sesión
                } else {
                    localStorage.removeItem('mi_token'); 
                    document.getElementById('vista-login').classList.remove('oculto');
                    document.getElementById('vista-app').classList.add('oculto');
                }
            } else {
                document.getElementById('vista-login').classList.remove('oculto');
                document.getElementById('vista-app').classList.add('oculto');
            }
        });

        async function iniciarSesion() {
          const usuario = document.getElementById('usuario').value;
          const contrasena = document.getElementById('contrasena').value;
          const resp = await fetch('/api/login', {
            method: 'POST',
            headers: {'Content-Type':'application/x-www-form-urlencoded'},
            body: 'usuario=' + encodeURIComponent(usuario) + '&contrasena=' + encodeURIComponent(contrasena)
          });
          const datos = await resp.json();
          if (datos.ok) {
            idUsuario = datos.idUsuario;
            nombreUsuario = datos.nombre;
            localStorage.setItem('mi_token', datos.token); 
            document.getElementById('nombre-usuario').textContent = nombreUsuario;
            document.getElementById('vista-login').classList.add('oculto');
            document.getElementById('vista-app').classList.remove('oculto');
            document.getElementById('error-login').classList.add('oculto');
            cargarTareas();
            cargarListaUsuarios(); // Cargar la lista al loguearse
          } else {
            document.getElementById('error-login').classList.remove('oculto');
          }
        }

        async function cerrarSesion() {
          if (idUsuario) { 
            const token = localStorage.getItem('mi_token');
            if (token) {
              await fetch('/api/session?token=' + token, { method: 'DELETE' });
              localStorage.removeItem('mi_token');
            }
          }
          idUsuario = null;
          nombreUsuario = null;
          document.getElementById('vista-app').classList.add('oculto');
          document.getElementById('vista-login').classList.remove('oculto');
          document.getElementById('usuario').value = '';
          document.getElementById('contrasena').value = '';
        }

        // --- NUEVA FUNCIÓN: CARGAR USUARIOS PARA EL SELECT ---
        async function cargarListaUsuarios() {
            const resp = await fetch('/api/usuarios');
            const usuarios = await resp.json();
            const select = document.getElementById('select-usuarios');
            select.innerHTML = '<option value="">Seleccione un usuario...</option>';
            usuarios.forEach(u => {
                if (u.idUsuario !== idUsuario) { // No listarse a uno mismo
                    const option = document.createElement('option');
                    option.value = u.idUsuario;
                    option.textContent = u.nombreUsuario;
                    select.appendChild(option);
                }
            });
        }

        async function cargarTareas() {
          if (!idUsuario) return;
          const resp = await fetch('/api/tareas?idUsuario=' + idUsuario);
          const tareas = await resp.json();
          const contenedor = document.getElementById('lista-tareas');
          contenedor.innerHTML = '';
          if (tareas.length === 0) {
            contenedor.innerHTML = '<div class="tarjeta">No tenés tareas creadas ni asignadas.</div>';
            return;
          }
          
          tareas.forEach(t => {
            const div = document.createElement('div');
            div.className = 'tarea';
            const titulo = escaparHtml(t.titulo);
            const descripcion = escaparHtml(t.descripcion);
            
            div.innerHTML = `
              <div class="tarea-header">
                <strong>` + titulo + `</strong>
                <span class="estado ` + t.estado + `">` + t.estado + `</span>
              </div>
              <p style="color:#4b5563; font-size:.9rem;">` + descripcion + `</p>
              <div class="fila fila-acciones" style="justify-content: flex-end;"></div>
            `;
            
            const btnEditar = document.createElement('button');
            btnEditar.textContent = 'Editar / Asignar';
            btnEditar.onclick = () => editarTarea(t.idTarea, t.titulo, t.descripcion, t.estado);
            
            const btnEliminar = document.createElement('button');
            btnEliminar.className = 'peligro';
            btnEliminar.textContent = 'Eliminar';
            btnEliminar.onclick = () => eliminarTarea(t.idTarea);
            
            const fila = div.querySelector('.fila-acciones');
            fila.appendChild(btnEditar);
            fila.appendChild(btnEliminar);

            contenedor.appendChild(div);
          });
        }

        async function crearTarea() {
          const titulo = document.getElementById('nueva-titulo').value;
          const descripcion = document.getElementById('nueva-descripcion').value;
          if (!titulo.trim()) return;
          await fetch('/api/tareas', {
            method: 'POST',
            headers: {'Content-Type':'application/x-www-form-urlencoded'},
            body: 'idUsuario=' + idUsuario + '&titulo=' + encodeURIComponent(titulo) + '&descripcion=' + encodeURIComponent(descripcion)
          });
          document.getElementById('nueva-titulo').value = '';
          document.getElementById('nueva-descripcion').value = '';
          cargarTareas();
        }

        async function editarTarea(idTarea, titulo, descripcion, estado) {
            const body = 'idTarea=' + idTarea + '&idUsuario=' + idUsuario + '&nombreUsuario=' + encodeURIComponent(nombreUsuario);
            const resp = await fetch('/api/lock', {
                method: 'POST',
                headers: {'Content-Type':'application/x-www-form-urlencoded'},
                body: body
            });

            if (resp.ok) {
                document.getElementById('edit-id-tarea').value = idTarea;
                document.getElementById('edit-titulo').value = titulo;
                document.getElementById('edit-descripcion').value = descripcion;
                document.getElementById('edit-estado').value = estado;
                // Reseteamos el select de usuarios
                document.getElementById('select-usuarios').value = "";
                document.getElementById('modal-edicion').classList.remove('oculto');
            } else if (resp.status === 403) {
                alert('No tienes permiso para editar esta tarea.');
            } else if (resp.status === 409) {
                alert('La tarea está siendo editada por otro usuario. Inténtalo más tarde.');
            } else {
                alert('Error al intentar bloquear la tarea.');
            }
        }

        // --- NUEVA FUNCIÓN: ASIGNAR TAREA AL USUARIO SELECCIONADO ---
        async function asignarTarea() {
            const idTarea = document.getElementById('edit-id-tarea').value;
            const idUsuarioDestino = document.getElementById('select-usuarios').value;

            if (!idUsuarioDestino) {
                alert("Por favor, selecciona un usuario de la lista.");
                return;
            }

            const resp = await fetch('/api/asignar', {
                method: 'POST',
                headers: {'Content-Type':'application/x-www-form-urlencoded'},
                body: 'idUsuario=' + idUsuario + '&idTarea=' + idTarea + '&idUsuarioDestino=' + idUsuarioDestino
            });

            const datos = await resp.json();
            if (datos.ok) {
                alert('¡Tarea asignada con éxito al colaborador!');
                document.getElementById('select-usuarios').value = ""; // Limpiar select
            } else {
                alert(datos.message || 'Error al asignar: Solo el creador de la tarea puede asignarla.');
            }
        }

        async function guardarCambios() {
            const idTarea = document.getElementById('edit-id-tarea').value;
            const estado = document.getElementById('edit-estado').value;

            await fetch('/api/tareas', {
                method: 'PUT',
                headers: {'Content-Type':'application/x-www-form-urlencoded'},
                body: 'idUsuario=' + idUsuario + '&idTarea=' + idTarea + '&estado=' + estado
            });
            
            await cancelarEdicion(); 
        }

        async function cancelarEdicion() {
            const idTarea = document.getElementById('edit-id-tarea').value;
            document.getElementById('modal-edicion').classList.add('oculto');
            
            const params = 'idTarea=' + idTarea + '&idUsuario=' + idUsuario + '&nombreUsuario=' + encodeURIComponent(nombreUsuario);
            await fetch('/api/lock?' + params, { method: 'DELETE' });
            
            cargarTareas(); 
        }

        async function eliminarTarea(idTarea) {
          if (!confirm('¿Estás seguro de que deseas eliminar esta tarea? (Esto también la eliminará para los usuarios asignados)')) return;
          
          await fetch('/api/tareas', {
            method: 'DELETE',
            headers: {'Content-Type':'application/x-www-form-urlencoded'},
            body: 'idUsuario=' + idUsuario + '&idTarea=' + idTarea
          });
          cargarTareas();
        }

        function escaparHtml(texto) {
          if (texto === null || typeof texto === 'undefined') return '';
          const div = document.createElement('div');
          div.textContent = texto;
          return div.innerHTML;
        }
        </script>
        </body>
        </html>
        """;
}