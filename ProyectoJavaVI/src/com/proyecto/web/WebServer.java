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
import java.util.concurrent.Executors;

import com.proyecto.server.DatabaseManager;
import com.proyecto.shared.Task;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * WebServer: INTERFAZ WEB SOBRE EL MISMO NÚCLEO DE LA APLICACIÓN
 *
 * No reemplaza a TaskServer (sockets): lo complementa. Ambos pueden correr
 * al mismo tiempo porque los dos usan DatabaseManager.getInstance(), que ya
 * está sincronizado. Esto demuestra que la capa de concurrencia sirve para
 * cualquier tipo de cliente, no solo para el cliente de consola.
 */
public class WebServer {

    private static final int PUERTO_WEB = 8080;

    public static void iniciar() throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(PUERTO_WEB), 0);
        servidor.createContext("/", WebServer::manejarIndex);
        servidor.createContext("/api/login", WebServer::manejarLogin);
        servidor.createContext("/api/tareas", WebServer::manejarTareas);
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
            responder(exchange, 200, "application/json",
                    "{\"ok\":true,\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + escaparJson(usuario) + "\"}");
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
                boolean ok = bd.actualizarEstadoTarea(idTarea, datos.getOrDefault("estado", ""), idUsuario);
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

    // ---------- utilidades ----------

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
          label { display:block; font-size:.85rem; margin-bottom:4px; color:#374151; }
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
        </style>
        </head>
        <body>
        <div class="contenedor">
          <h1>Gestión de Tareas</h1>
          <div class="subtitulo">Interfaz web sobre el mismo DatabaseManager sincronizado del proyecto</div>

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
              <label>Título</label>
              <input id="nueva-titulo">
              <label>Descripción</label>
              <textarea id="nueva-descripcion" rows="2"></textarea>
              <button onclick="crearTarea()">Crear tarea</button>
            </div>

            <div id="lista-tareas"></div>
          </div>
        </div>

        <script>
        let idUsuario = null;

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
            document.getElementById('nombre-usuario').textContent = datos.nombre;
            document.getElementById('vista-login').classList.add('oculto');
            document.getElementById('vista-app').classList.remove('oculto');
            document.getElementById('error-login').classList.add('oculto');
            cargarTareas();
          } else {
            document.getElementById('error-login').classList.remove('oculto');
          }
        }

        function cerrarSesion() {
          idUsuario = null;
          document.getElementById('vista-app').classList.add('oculto');
          document.getElementById('vista-login').classList.remove('oculto');
          document.getElementById('usuario').value = '';
          document.getElementById('contrasena').value = '';
        }

        async function cargarTareas() {
          const resp = await fetch('/api/tareas?idUsuario=' + idUsuario);
          const tareas = await resp.json();
          const contenedor = document.getElementById('lista-tareas');
          contenedor.innerHTML = '';
          if (tareas.length === 0) {
            contenedor.innerHTML = '<div class="tarjeta">No tenés tareas creadas.</div>';
            return;
          }
          tareas.forEach(t => {
            const div = document.createElement('div');
            div.className = 'tarea';
            div.innerHTML = `
              <div class="tarea-header">
                <strong>${escaparHtml(t.titulo)}</strong>
                <span class="estado ${t.estado}">${t.estado}</span>
              </div>
              <p style="color:#4b5563; font-size:.9rem;">${escaparHtml(t.descripcion)}</p>
              <div class="fila">
                <select onchange="actualizarEstado(${t.idTarea}, this.value)">
                  <option value="PENDIENTE" ${t.estado==='PENDIENTE'?'selected':''}>PENDIENTE</option>
                  <option value="EN_PROGRESO" ${t.estado==='EN_PROGRESO'?'selected':''}>EN_PROGRESO</option>
                  <option value="COMPLETADA" ${t.estado==='COMPLETADA'?'selected':''}>COMPLETADA</option>
                </select>
                <button class="peligro" onclick="eliminarTarea(${t.idTarea})">Eliminar</button>
              </div>`;
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

        async function actualizarEstado(idTarea, estado) {
          await fetch('/api/tareas', {
            method: 'PUT',
            headers: {'Content-Type':'application/x-www-form-urlencoded'},
            body: 'idUsuario=' + idUsuario + '&idTarea=' + idTarea + '&estado=' + estado
          });
          cargarTareas();
        }

        async function eliminarTarea(idTarea) {
          await fetch('/api/tareas', {
            method: 'DELETE',
            headers: {'Content-Type':'application/x-www-form-urlencoded'},
            body: 'idUsuario=' + idUsuario + '&idTarea=' + idTarea
          });
          cargarTareas();
        }

        function escaparHtml(texto) {
          const div = document.createElement('div');
          div.textContent = texto;
          return div.innerHTML;
        }
        </script>
        </body>
        </html>
        """;
}