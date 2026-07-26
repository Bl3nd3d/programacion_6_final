# Sistema de Gestión de Tareas Distribuidas - Programación VI

## 📋 Descripción del Proyecto

Este es un sistema de **gestión de tareas colaborativo** con arquitectura cliente-servidor implementado en Java. Demuestra los conceptos fundamentales de:

- **Arquitectura Cliente-Servidor**: Separación clara entre cliente y servidor
- **Programación Concurrente**: Manejo de múltiples clientes simultáneamente con threads
- **Sincronización**: Prevención de race conditions en acceso a BD
- **Persistencia de Datos**: Base de datos MySQL/PostgreSQL
- **Autenticación**: Sistema de login de usuario

## 🏗️ Estructura del Proyecto

```
ProyectoJavaVI/
├── src/
│   ├── com/proyecto/server/
│   │   ├── TaskServer.java          # Servidor multihilo
│   │   ├── ClientHandler.java       # Manejador de cada cliente (thread)
│   │   └── DatabaseManager.java     # Gestor de BD con sincronización
│   ├── com/proyecto/client/
│   │   └── TaskClient.java          # Cliente de consola
│   └── com/proyecto/shared/
│       └── Task.java                # POJO de Tarea
├── database/
│   └── schema.sql                   # Script de creación de BD
└── README.md
```

## 🛠️ Requisitos Previos

Antes de ejecutar este proyecto, asegúrate de tener instalado:

1. **Java JDK 11 o superior**
   - Verifica con: `java -version`
   - Descarga desde: https://www.oracle.com/java/technologies/downloads/

2. **MySQL Server 5.7+ o PostgreSQL 10+**
   - Descarga desde: https://dev.mysql.com/downloads/mysql/ o https://www.postgresql.org/download/
   - Debe estar ejecutándose en tu máquina

3. **JDBC Driver para MySQL**
   - Descarga: mysql-connector-java-8.0.jar (o versión actual)
   - O en Maven: agregar dependencia de mysql-connector-java

4. **IDE Recomendado**
   - IntelliJ IDEA Community Edition (gratuito)
   - Eclipse
   - Visual Studio Code con extensión de Java

## 💾 Instalación y Configuración de Base de Datos

### Paso 1: Crear la Base de Datos

1. Abre tu cliente MySQL (MySQL Workbench, phpMyAdmin o línea de comandos)

2. Ejecuta el archivo `schema.sql` completo:
   ```sql
   -- Copia todo el contenido del archivo schema.sql y ejecuta
   ```

   O desde línea de comandos:
   ```bash
   mysql -u root -p < schema.sql
   ```

3. Verifica que las tablas se crearon:
   ```sql
   USE task_manager;
   SHOW TABLES;
   ```

   Deberías ver: `auditoria`, `tareas`, `usuarios`

### Paso 2: Descargar y Configurar JDBC Driver

El driver JDBC para MySQL es gestionado automáticamente por Maven si utilizas `pom.xml`. Si no estás usando Maven y necesitas compilar manualmente sin él (lo cual no es el enfoque recomendado para este proyecto), entonces puedes descargar el JAR manualmente.

1.  **Usar Maven (Recomendado)**
    - Asegúrate de que la siguiente dependencia esté en tu `pom.xml`:
      ```xml
      <dependency>
          <groupId>mysql</groupId>
          <artifactId>mysql-connector-java</artifactId>
          <version>8.0.33</version>
      </dependency>
      ```
    - Maven descargará el JAR automáticamente durante el proceso de compilación. No necesitas descargarlo manualmente ni colocarlo en la carpeta `lib/`.

2.  **Opción Manual (Solo si no usas Maven)**
    - Descarga `mysql-connector-java-8.0.x.jar` desde https://dev.mysql.com/downloads/connector/j/
    - Coloca el JAR en una carpeta del proyecto (ej: `lib/`)


### Paso 3: Configurar credenciales de Base de Datos

La configuración de la base de datos se gestiona a través de **variables de entorno**. Esto permite configurar la conexión sin modificar el código fuente, lo cual es una buena práctica.

Puedes configurar las siguientes variables antes de ejecutar el servidor:

-   `DB_URL`: La URL de conexión JDBC.
    -   *Default*: `jdbc:mysql://localhost:3306/task_manager`
-   `DB_USER`: El usuario de la base de datos.
    -   *Default*: `root`
-   `DB_PASSWORD`: La contraseña del usuario.
    -   *Default*: *(vacío)*

**Ejemplo de cómo iniciar el servidor con configuración personalizada:**

**En Windows (Command Prompt):**
```cmd
set DB_URL=jdbc:mysql://mi_host:3306/mi_db
set DB_USER=mi_usuario
set DB_PASSWORD=mi_contraseña
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.server.TaskServer
```

**En Windows (PowerShell):**
```powershell
$env:DB_URL="jdbc:mysql://mi_host:3306/mi_db"
$env:DB_USER="mi_usuario"
$env:DB_PASSWORD="mi_contraseña"
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.server.TaskServer
```

**En Linux o macOS:**
```bash
export DB_URL="jdbc:mysql://mi_host:3306/mi_db"
export DB_USER="mi_usuario"
export DB_PASSWORD="mi_contraseña"
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.server.TaskServer
```

Si no se definen estas variables, el sistema usará los valores por defecto que están en `DatabaseManager.java`.

**Nota**: Si usas PostgreSQL en lugar de MySQL, asegúrate de cambiar el `DB_URL` correspondientemente, por ejemplo: `jdbc:postgresql://localhost:5432/task_manager`.


# Compilación del Proyecto

### Opción 1: Con IDE (Recomendado para principiantes)

1. Abre tu IDE (IntelliJ, Eclipse, etc.)
2. Importa el proyecto
3. Asegúrate de que el JDBC driver está en el classpath
4. La IDE compilará automáticamente al presionar Run

### Opción 2: Desde línea de comandos

```bash
# Navega a la carpeta del proyecto
cd ProyectoJavaVI

# Compila todas las clases
javac -d bin -cp lib/mysql-connector-java-8.0.33.jar src/com/proyecto/**/*.java
```

## 🚀 Ejecución

### PASO 1: Inicia el Servidor (en una terminal)

```bash
# Si compilaste con IDE, simplemente ejecuta TaskServer.java

# Si compilaste desde línea de comandos:
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.server.TaskServer
```

Deberías ver:
```
═══════════════════════════════════════════════
      SERVIDOR DE GESTIÓN DE TAREAS INICIADO
═══════════════════════════════════════════════
[SERVIDOR] Escuchando en puerto: 5555
[SERVIDOR] Máximo de clientes: 50
[SERVIDOR] Base de datos: task_manager
═══════════════════════════════════════════════
```

**El servidor debe seguir ejecutándose**

### PASO 2: Abre nuevas terminales para cada cliente

En terminal 2:
```bash
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.client.TaskClient
```

En terminal 3 (opcional, para probar con otro usuario):
```bash
java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.client.TaskClient
```

### PASO 3: Usa el sistema

Para la primera terminal del cliente:

```
════════════════════════════════════════════════════════
        CLIENTE DE GESTIÓN DE TAREAS DISTRIBUIDAS      
════════════════════════════════════════════════════════
[INFO] Conectándose al servidor...
[INFO] Usuario: admin, Contraseña: admin123
[INFO] Usuario: usuario1, Contraseña: pass123
[INFO] Usuario: usuario2, Contraseña: pass456

========================================
     SISTEMA DE GESTIÓN DE TAREAS       
========================================
Ingrese usuario:
admin
Ingrese contraseña:
admin123
[ÉXITO] Bienvenido admin!

========================================
MENÚ PRINCIPAL - Usuario: admin
========================================
1. Listar mis tareas
2. Crear nueva tarea
3. Actualizar estado de tarea
4. Eliminar tarea
5. Cerrar sesión
----------------------------------------
Seleccione opción:
1
```

## 🧵 Demostración de Concurrencia

Para ver la concurrencia en acción:

1. Inicia el servidor (veras "Threads activos")
2. Abre 3-5 clientes simultáneamente
3. En el servidor, observarás:
   ```
   [SERVIDOR] Cliente conectado: 192.168.1.100
   [SERVIDOR] Threads activos: 2 | Clientes: 1
   [SERVIDOR] Cliente conectado: 192.168.1.101
   [SERVIDOR] Threads activos: 3 | Clientes: 2
   ```
4. Mientras un cliente crea tareas y otro las modifica simultáneamente, el sistema maneja ambos sin conflictos
5. La sincronización en `DatabaseManager` previene race conditions

### Prueba de stress (Concurrencia)

Crea un script que abra múltiples clientes automáticamente y veras cómo el servidor maneja miles de operaciones concurrentes sin data corruption.

## 📊 Conceptos de Programación VI Implementados

### 1. ARQUITECTURA CLIENTE-SERVIDOR ✓
- TaskServer: Escucha en puerto 5555
- TaskClient: Se conecta al servidor
- Comunicación bidireccional mediante Sockets TCP/IP

### 2. CONCURRENCIA (HILOS/THREADS) ✓
En `TaskServer.java`:
```java
// Cada cliente obtiene su propio thread
Thread threadCliente = new Thread(manejador, "Cliente-" + (clientesActivos.size() + 1));
threadCliente.start(); // Inicia ejecución concurrente
```

Esto permite que 100 clientes se ejecuten en paralelo, cada uno en su propio hilo.

### 3. SINCRONIZACIÓN ✓
En `DatabaseManager.java`, TODOS los métodos son synchronized:
```java
public synchronized boolean crearTarea(...) {
    // Solo UN thread puede ejecutar esto a la vez
    // Si Thread A está creando una tarea, Thread B espera su turno
}
```

Esto previene **race conditions** (dos threads modificando la BD simultáneamente).

### 4. LÓGICA DE NEGOCIO ✓
- Sistema de gestión de tareas con estados (PENDIENTE, EN_PROGRESO, COMPLETADA)
- Cada usuario solo ve sus propias tareas
- Auditoría de cambios en tabla de auditoria

### 5. PERSISTENCIA EN BD ✓
- Usuarios guardados en tabla `usuarios`
- Tareas persistidas en tabla `tareas`
- Cambios registrados en tabla `auditoria`
- Integridad referencial con foreign keys

### 6. LOGIN DE USUARIO ✓
- Autenticación con usuario y contraseña
- Validación en base de datos
- Session management por cliente

## 🔍 Archivos Clave para Defensa del Examen

Cuando el profesor pregunte "¿Dónde está la concurrencia?", señala estos archivos:

### 1. TaskServer.java (línea 57-70)
```java
// AQUÍ SE CREA EL THREAD PARA CADA CLIENTE
Thread threadCliente = new Thread(manejador, "Cliente-" + ...);
threadCliente.start();
```

### 2. DatabaseManager.java (todos los métodos)
```java
// AQUÍ ESTÁ LA SINCRONIZACIÓN
public synchronized int autenticarUsuario(...) {
    // Garantiza que un thread a la vez accede a BD
}
```

### 3. ClientHandler.java
```java
// Cada método que entra aquí está siendo ejecutado por un thread diferente
@Override
public void run() {
    // Este código corre en su PROPIO thread
}
```

## 🐛 Troubleshooting

| Problema | Solución |
|----------|----------|
| "Connection refused" | Verifica que el servidor está ejecutándose |
| "No suitable driver found" | Asegúrate de que mysql-connector-java.jar está en classpath |
| "Access denied for user 'root'" | Verifica usuario y contraseña en DatabaseManager.java |
| "Unknown database 'task_manager'" | Ejecuta el archivo schema.sql en tu BD |
| Puerto 5555 en uso | Cambia PUERTO en TaskServer.java a otro valor (ej: 6666) |

## 📝 Notas Importantes para el Examen

1. **El servidor debe estar ejecutándose ANTES de conectar clientes**
2. **Prueba con al menos 2-3 clientes simultáneos para demostrar concurrencia**
3. **Si hay cambios en la BD durante el examen, verifica que la sincronización previene errores**
4. **Tener lista la explicación de dónde ocurre la sincronización**

## 🎓 Recursos Adicionales

- **Oracle Java Threads**: https://docs.oracle.com/javase/tutorial/essential/concurrency/
- **Socket Programming**: https://docs.oracle.com/javase/tutorial/networking/sockets/
- **JDBC**: https://docs.oracle.com/javase/tutorial/jdbc/
- **Synchronized Keyword**: https://docs.oracle.com/javase/tutorial/essential/concurrency/syncmeth.html

---

**Creado para Programación VI** | **Examen Final**
