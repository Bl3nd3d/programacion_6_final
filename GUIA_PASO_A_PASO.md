# 📚 GUÍA COMPLETA PASO A PASO
## Sistema de Gestión de Tareas Distribuidas - Examen Programación VI

Esta guía te llevará de la mano a través de cada paso necesario para tener tu proyecto funcionando perfectamente en el examen.

---

## PARTE 1: PREPARACIÓN (15 minutos)

### Paso 1.1: Verificar que tienes Java instalado

Abre una terminal (CMD en Windows, Terminal en Mac/Linux) y escribe:

```bash
java -version
```

Deberías ver algo como:
```
java version "11.0.15" 2022-04-19 LTS
Java(TM) SE Runtime Environment 18.9 (build 11.0.15+10-LTS-283)
Java HotSpot(TM) 64-Bit Server VM 18.9 (build 11.0.15+10-LTS-283, mixed mode)
```

Si no ves esto, necesitas descargar Java desde https://www.oracle.com/java/technologies/downloads/. Elige JDK (no JRE), elige tu sistema operativo, y sigue el instalador.

**Verifica que JDK instaló correctamente** escribiendo nuevamente `java -version`.

### Paso 1.2: Verificar que tienes MySQL instalado y ejecutándose

En terminal, escribe:

```bash
mysql -u root -p
```

Te pedirá contraseña. Ingresa tu contraseña de MySQL (generalmente está vacía si es instalación nueva). Si entra correctamente, verás:

```
mysql>
```

Escribe `exit;` y presiona Enter para salir.

Si no funciona, descarga MySQL desde https://dev.mysql.com/downloads/mysql/ e instálalo. En Windows, ejecuta "MySQL 8.0 Command Line Client" después de instalar.

### Paso 1.3: Organiza tu carpeta de proyecto

Crea una estructura así:

```
TuProyecto/
├── src/
│   └── com/proyecto/
│       ├── server/
│       ├── client/
│       └── shared/
├── bin/                 (se crea automaticamente)
├── lib/                 (para JDBC driver)
├── database/
├── dist/                (se crea automaticamente)
├── README.md
├── EXPLICACION_CONCURRENCIA.md
└── compile.sh
```

**En Windows**: Crea estas carpetas en Explorador de Archivos
**En Mac/Linux**: En terminal, ejecuta:
```bash
mkdir -p TuProyecto/src/com/proyecto/{server,client,shared}
mkdir -p TuProyecto/lib
mkdir -p TuProyecto/database
cd TuProyecto
```

---

## PARTE 2: DESCARGAR EL JDBC DRIVER (10 minutos)

El JDBC driver es lo que permite que Java se comunique con MySQL.

### Opción A: Descarga Manual (Recomendado)

1. Ve a https://dev.mysql.com/downloads/connector/j/
2. Busca "mysql-connector-java" versión 8.0.33
3. Descarga el archivo JAR
4. Colócalo en la carpeta `lib/` de tu proyecto

Debería quedar: `TuProyecto/lib/mysql-connector-java-8.0.33.jar`

### Opción B: Desde Terminal (Avanzado)

```bash
cd TuProyecto/lib
# En Mac/Linux:
wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-8.0.33.jar

# En Windows (si tienes curl):
curl -O https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-8.0.33.jar
```

**Verifica que está:** En la carpeta `lib/` debe haber un archivo de ~2.3 MB.

---

## PARTE 3: COPIAR ARCHIVOS JAVA (10 minutos)

Coloca los archivos Java en sus respectivas carpetas:

```
src/com/proyecto/
├── server/
│   ├── TaskServer.java
│   ├── ClientHandler.java
│   └── DatabaseManager.java
├── client/
│   └── TaskClient.java
└── shared/
    └── Task.java
```

**Importante**: Los nombres de paquete en los archivos deben coincidir con las carpetas:
- `TaskServer.java` debe tener `package com.proyecto.server;`
- `TaskClient.java` debe tener `package com.proyecto.client;`
- `Task.java` debe tener `package com.proyecto.shared;`

Los archivos que descargaste ya tienen estos packages correctos, así que solo tienes que colocarlos en las carpetas correctas.

---

## PARTE 4: CONFIGURAR BASE DE DATOS (15 minutos)

### Paso 4.1: Conectar a MySQL

En terminal:
```bash
mysql -u root -p
```

Ingresa tu contraseña (si es nueva, típicamente está vacía, solo presiona Enter).

### Paso 4.2: Ejecutar el script de BD

Una vez dentro de MySQL (ves `mysql>`), ejecuta:

```sql
CREATE DATABASE IF NOT EXISTS task_manager;
USE task_manager;

CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario INT PRIMARY KEY AUTO_INCREMENT,
    nombre_usuario VARCHAR(50) UNIQUE NOT NULL,
    contrasena VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tareas (
    id_tarea INT PRIMARY KEY AUTO_INCREMENT,
    id_usuario INT NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    estado ENUM('PENDIENTE', 'EN_PROGRESO', 'COMPLETADA') DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    INDEX idx_usuario (id_usuario),
    INDEX idx_estado (estado)
);

CREATE TABLE IF NOT EXISTS auditoria (
    id_auditoria INT PRIMARY KEY AUTO_INCREMENT,
    id_usuario INT,
    accion VARCHAR(100),
    descripcion TEXT,
    fecha_accion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE SET NULL
);

INSERT INTO usuarios (nombre_usuario, contrasena, email) VALUES
('admin', 'admin123', 'admin@empresa.com'),
('usuario1', 'pass123', 'usuario1@empresa.com'),
('usuario2', 'pass456', 'usuario2@empresa.com');

INSERT INTO tareas (id_usuario, titulo, descripcion, estado) VALUES
(1, 'Configurar Servidor', 'Implementar servidor multihilo', 'EN_PROGRESO'),
(1, 'Pruebas de Carga', 'Realizar pruebas de concurrencia', 'PENDIENTE'),
(2, 'Documentación API', 'Escribir documentación completa', 'PENDIENTE');
```

Luego escribe:
```bash
exit;
```

**Verifica que funcionó**: Abre MySQL de nuevo y escribe:
```sql
USE task_manager;
SHOW TABLES;
```

Deberías ver: `auditoria`, `tareas`, `usuarios`

### Paso 4.3: Configurar credenciales en DatabaseManager.java

Abre el archivo `src/com/proyecto/server/DatabaseManager.java`

Busca estas líneas (alrededor de la línea 25):

```java
private static final String URL = "jdbc:mysql://localhost:3306/task_manager";
private static final String USER = "root";
private static final String PASSWORD = "";
```

Modifica según tu configuración MySQL:
- `USER`: el usuario MySQL (típicamente "root")
- `PASSWORD`: tu contraseña MySQL (vacía si no la modificaste)

**Ejemplo si tu MySQL tiene contraseña "micontraseña":**
```java
private static final String PASSWORD = "micontraseña";
```

**Guarda el archivo**.

---

## PARTE 5: COMPILAR EL CÓDIGO (10 minutos)

### Opción A: Desde Terminal (Recomendado)

Abre terminal en la carpeta del proyecto y escribe:

```bash
# En Windows (Command Prompt):
javac -d bin -cp "lib\*" src\com\proyecto\shared\Task.java
javac -d bin -cp "lib\*" src\com\proyecto\server\*.java
javac -d bin -cp "lib\*" src\com\proyecto\client\*.java

# En Mac/Linux (Terminal):
javac -d bin -cp "lib/*" src/com/proyecto/shared/Task.java
javac -d bin -cp "lib/*" src/com/proyecto/server/*.java
javac -d bin -cp "lib/*" src/com/proyecto/client/*.java
```

Si no ves ningún error, ¡la compilación fue exitosa! 

**Verifica**: Abre la carpeta `bin/` y deberías ver:
```
bin/
└── com/proyecto/
    ├── server/
    │   ├── TaskServer.class
    │   ├── ClientHandler.class
    │   └── DatabaseManager.class
    ├── client/
    │   └── TaskClient.class
    └── shared/
        └── Task.class
```

### Opción B: Con IDE (IntelliJ o Eclipse)

Si usas una IDE, típicamente:
1. Abre el proyecto
2. Click derecho en el proyecto → Build/Compile
3. La IDE compilará automáticamente

---

## PARTE 6: EJECUTAR EL SISTEMA (5 minutos)

### Paso 6.1: Inicia el SERVIDOR

**EN UNA TERMINAL**, navega a tu carpeta del proyecto y ejecuta:

```bash
# Windows (Command Prompt):
java -cp "bin;lib\mysql-connector-java-8.0.33.jar" com.proyecto.server.TaskServer

# Mac/Linux (Terminal):
java -cp "bin:lib/mysql-connector-java-8.0.33.jar" com.proyecto.server.TaskServer
```

Deberías ver algo como:

```
═══════════════════════════════════════════════
      SERVIDOR DE GESTIÓN DE TAREAS INICIADO
═══════════════════════════════════════════════
[SERVIDOR] Escuchando en puerto: 5555
[SERVIDOR] Máximo de clientes: 50
[SERVIDOR] Base de datos: task_manager
═══════════════════════════════════════════════

[SERVIDOR] Cliente conectado: 127.0.0.1  ← Espera por cliente
```

**IMPORTANTE: Deja esta terminal abierta. El servidor debe estar ejecutándose.**

### Paso 6.2: Inicia un CLIENTE

**EN UNA TERMINAL DIFERENTE** (abre una nueva terminal, NO cierres la anterior):

```bash
# Windows (Command Prompt):
java -cp "bin;lib\mysql-connector-java-8.0.33.jar" com.proyecto.client.TaskClient

# Mac/Linux (Terminal):
java -cp "bin:lib/mysql-connector-java-8.0.33.jar" com.proyecto.client.TaskClient
```

Verás:

```
════════════════════════════════════════════════════════
        CLIENTE DE GESTIÓN DE TAREAS DISTRIBUIDAS      
════════════════════════════════════════════════════════
[INFO] Conectándose al servidor...
[INFO] Usuario: admin, Contraseña: admin123
[INFO] Usuario: usuario1, Contraseña: pass123
[INFO] Usuario: usuario2, Contraseña: pass456

[CLIENTE] Intentando conectar a localhost:5555...
[CLIENTE] ✓ Conectado al servidor correctamente

========================================
     SISTEMA DE GESTIÓN DE TAREAS       
========================================
Ingrese usuario:
```

### Paso 6.3: Ingresa Credenciales

Escribe:
```
usuario: admin
contraseña: admin123
```

Deberías ver:

```
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
```

**¡FELICIDADES! Tu sistema está funcionando.**

---

## PARTE 7: PROBAR FUNCIONALIDADES (10 minutos)

### Test 1: Listar Tareas

Escribe: `1`

Verás las tareas del usuario admin:

```
========== TUS TAREAS ==========
ID: 1 | Configurar Servidor | Estado: EN_PROGRESO
  Descripción: Implementar servidor multihilo
ID: 2 | Pruebas de Carga | Estado: PENDIENTE
  Descripción: Realizar pruebas de concurrencia
```

### Test 2: Crear Nueva Tarea

Escribe: `2`

Luego completa:
```
Ingrese título de la tarea:
Mi nueva tarea

Ingrese descripción:
Descripción de la tarea
```

Verás: `[ÉXITO] Tarea creada correctamente`

### Test 3: Actualizar Tarea

Escribe: `3`

Luego completa:
```
Ingrese ID de la tarea a actualizar:
1

Seleccione nuevo estado:
1. PENDIENTE
2. EN_PROGRESO
3. COMPLETADA
Seleccione: 2
```

Verás: `[ÉXITO] Tarea actualizada a: EN_PROGRESO`

### Test 4: DEMOSTRAR CONCURRENCIA

**En una nueva terminal**, abre OTRO cliente:

```bash
java -cp "bin:lib/mysql-connector-java-8.0.33.jar" com.proyecto.client.TaskClient
```

Ingresa con usuario diferente:
```
usuario: usuario1
contraseña: pass123
```

Ahora tienes DOS clientes conectados. **En el servidor, verás:**

```
[SERVIDOR] Clientes activos: 2
[SERVIDOR] Threads activos: 3
```

**En cliente 1 (admin)**: Crea una tarea
**En cliente 2 (usuario1)**: Crea una tarea simultáneamente

Ambos deberían poder crear sin problemas. **Esto demuestra que tu servidor maneja concurrencia.**

---

## PARTE 8: PUNTOS CLAVE PARA EL EXAMEN

Cuando el profesor pregunte, tienes preparadas estas respuestas:

### "¿Dónde está la concurrencia?"

Señala `TaskServer.java` línea ~69:

```java
Thread threadCliente = new Thread(manejador, "Cliente-" + (clientesActivos.size() + 1));
threadCliente.start();
```

Explica: "Aquí creamos un nuevo thread para cada cliente. Cada thread corre su propia copia del método `run()` de `ClientHandler`. Si tienes 100 clientes, tienes 100 threads ejecutándose en paralelo."

### "¿Cómo evitas conflictos de datos?"

Señala `DatabaseManager.java` y explica:

"Todos los métodos de acceso a base de datos son `synchronized`. Esto significa que solo un thread puede ejecutar el método a la vez. Si dos threads intentan insertar una tarea simultáneamente, uno obtiene el candado, modifica la BD, y libera el candado. El otro espera pacientemente. Esto previene race conditions y corrupción de datos."

### "¿Qué ocurriría sin sincronización?"

"Sin sincronización, dos threads podrían leer el mismo contador de ID, ambos crearían registros con el mismo ID, corrompiendo la base de datos. Más grave, podrían ocurrir inconsistencias en los datos transversales."

### "¿Cómo previene deadlocks?"

"El diseño está libre de deadlocks porque todos los locks están en un lugar (`DatabaseManager`), no hay locks anidados (un thread nunca obtiene lock A y luego intenta lock B), y todos los threads usan el mismo patrón de acceso. Esto elimina la posibilidad de circular wait que causa deadlocks."

---

## PARTE 9: TROUBLESHOOTING

| Problema | Solución |
|----------|----------|
| "Connection refused" | Asegúrate de que el SERVIDOR está ejecutándose en otra terminal |
| "No suitable driver found" | Verifica que `mysql-connector-java-8.0.33.jar` está en la carpeta `lib/` |
| "Access denied for user 'root'" | Modifica USER y PASSWORD en `DatabaseManager.java` con tus credenciales reales |
| "Unknown database 'task_manager'" | Ejecuta el script SQL en MySQL para crear las tablas |
| "Port 5555 already in use" | Otra aplicación está usando ese puerto. Cambia el puerto en `TaskServer.java` línea 29 |
| "Class not found: com.mysql.cj.jdbc.Driver" | El JAR de MySQL Connector no está correctamente en el classpath |
| Los clientes no pueden conectar | Verifica que el servidor está escuchando (debe decir "Escuchando en puerto 5555") |

---

## RESUMEN FINAL

Felicidades, acabas de construir un **sistema de gestión distribuida con programación concurrente** que cumple TODOS los requisitos:

✓ **Lógica de negocio**: Gestión de tareas compartidas
✓ **Arquitectura Cliente-Servidor**: TaskServer y TaskClient comunicándose por sockets
✓ **Login de usuario**: Autenticación antes de acceso
✓ **Persistencia en BD**: MySQL con 3 tablas
✓ **Programación concurrente**: Threads, sincronización, prevención de race conditions
✓ **Funcional**: Todo compila y ejecuta correctamente

Durante el examen, asegúrate de demostrar:
1. Servidor escuchando
2. Múltiples clientes conectándose
3. Operaciones simultáneas sin errores
4. Capacidad de explicar la concurrencia y sincronización

**Mucho éxito en tu examen.**
