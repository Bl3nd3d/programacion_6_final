#!/bin/bash
# Script de Compilación - Sistema de Gestión de Tareas Distribuidas

set -e  # Detener si hay error

echo "════════════════════════════════════════════════════════════════"
echo "    SCRIPT DE COMPILACIÓN - Gestión de Tareas Distribuidas"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Verificar que Java está instalado
echo "[1/5] Verificando Java..."
if ! command -v javac &> /dev/null; then
    echo "ERROR: Java JDK no está instalado"
    echo "Descárgalo desde: https://www.oracle.com/java/technologies/downloads/"
    exit 1
fi
java -version
echo "✓ Java encontrado"
echo ""

# Crear estructura de directorios
echo "[2/5] Creando estructura de directorios..."
mkdir -p src/com/proyecto/server
mkdir -p src/com/proyecto/client
mkdir -p src/com/proyecto/shared
mkdir -p bin
mkdir -p lib
mkdir -p database
echo "✓ Directorios creados"
echo ""

# Descargar JDBC Driver si no existe
echo "[3/5] Verificando JDBC Driver de MySQL..."
JDBC_JAR="lib/mysql-connector-java-8.0.33.jar"
if [ ! -f "$JDBC_JAR" ]; then
    echo "Descargando mysql-connector-java..."
    # Nota: Puede variar según versión
    # Alternativamente, descárgalo manualmente desde:
    # https://dev.mysql.com/downloads/connector/j/
    echo "IMPORTANTE: Por favor descarga mysql-connector-java-8.0.33.jar"
    echo "y colócalo en la carpeta 'lib/' de este proyecto"
    echo ""
    echo "Alternativa: Si tienes Maven, agrega a pom.xml:"
    echo "<dependency>"
    echo "    <groupId>mysql</groupId>"
    echo "    <artifactId>mysql-connector-java</artifactId>"
    echo "    <version>8.0.33</version>"
    echo "</dependency>"
    echo ""
    # exit 1
fi
if [ -f "$JDBC_JAR" ]; then
    echo "✓ JDBC Driver encontrado"
else
    echo "⚠ JDBC Driver no encontrado, continuando (pero fallarán operaciones de BD)"
fi
echo ""

# Compilar proyecto
echo "[4/5] Compilando código Java..."

# Buscar todos los archivos .java en src/ y compilar
javac -d bin -cp "lib/*" src/com/proyecto/shared/*.java 2>&1 || true
javac -d bin -cp "lib/*:src" src/com/proyecto/server/*.java 2>&1 || true
javac -d bin -cp "lib/*:src" src/com/proyecto/client/*.java 2>&1 || true

if [ -f "bin/com/proyecto/server/TaskServer.class" ]; then
    echo "✓ Compilación exitosa"
else
    echo "ERROR: La compilación falló"
    echo "Verifica que todos los archivos .java estén en src/com/proyecto/[server|client|shared]/"
    exit 1
fi
echo ""

# Crear JAR (opcional pero útil)
echo "[5/5] Creando archivos JAR..."
mkdir -p dist

jar cvfe dist/TaskServer.jar com.proyecto.server.TaskServer -C bin . > /dev/null 2>&1 || true
jar cvfe dist/TaskClient.jar com.proyecto.client.TaskClient -C bin . > /dev/null 2>&1 || true

if [ -f "dist/TaskServer.jar" ]; then
    echo "✓ JARs creados en carpeta dist/"
fi
echo ""

echo "════════════════════════════════════════════════════════════════"
echo "                    ✓ COMPILACIÓN COMPLETADA"
echo "════════════════════════════════════════════════════════════════"
echo ""
echo "PRÓXIMOS PASOS:"
echo "1. Configura tu base de datos:"
echo "   - Ejecuta: mysql -u root -p < database/schema.sql"
echo ""
echo "2. Modifica DatabaseManager.java si es necesario:"
echo "   - URL: jdbc:mysql://localhost:3306/task_manager"
echo "   - USER: root"
echo "   - PASSWORD: [tu password]"
echo ""
echo "3. INICIA EL SERVIDOR (en una terminal):"
echo "   java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.server.TaskServer"
echo ""
echo "4. ABRE CLIENTE (en otra terminal):"
echo "   java -cp bin:lib/mysql-connector-java-8.0.33.jar com.proyecto.client.TaskClient"
echo ""
echo "5. Ingresa credenciales:"
echo "   Usuario: admin"
echo "   Contraseña: admin123"
echo ""
echo "════════════════════════════════════════════════════════════════"
