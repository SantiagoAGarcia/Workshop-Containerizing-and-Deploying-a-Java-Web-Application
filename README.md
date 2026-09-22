# virtualization-lab

Aplicación web Java construida con Spring Boot como parte del taller de
containerización y despliegue.

## Estado actual

- Aplicación Spring Boot con un endpoint `/greeting`.
- El puerto se toma de la variable de entorno `PORT`, con `6000` por defecto.

Pendiente: Dockerfile, Docker Compose, publicación en Docker Hub y despliegue en EC2.

## Requisitos

- Java 21
- Maven 3.9+

## Construir y ejecutar

```bash
mvn clean package
java -jar target/*.jar
```

## Probar el endpoint

```
http://localhost:6000/greeting?name=Pedro
```

Respuesta esperada:

```
Hello, Pedro!
```
