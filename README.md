# Bank API

Backend de una aplicación bancaria con **Java 21**, **Spring Boot 3**, **Spring Data JPA** y
**JWT**. Permite registrar usuarios, abrir cuentas, depositar, retirar y transferir dinero, con
un historial de movimientos por cuenta.

## Características

- Registro e inicio de sesión con contraseñas BCrypt y tokens JWT (sin estado).
- Varias cuentas por usuario, cada una con su moneda (código ISO de 3 letras).
- Depósitos, retiros y transferencias entre cuentas de cualquier usuario.
- Libro mayor: cada movimiento guarda el saldo resultante; las dos patas de una transferencia
  comparten una referencia.
- Documentación interactiva con Swagger UI.

## Decisiones de diseño

- **Dinero en centavos (`long`).** Nunca `double`. La API recibe y devuelve importes decimales
  con dos decimales (`BigDecimal`).
- **Saldos seguros ante concurrencia.** El saldo cambia con un único `UPDATE` condicional
  (`... WHERE saldo + delta >= 0`), de modo que validar y modificar es una sola operación
  atómica. Además hay un `CHECK (balance_cents >= 0)` en la tabla.
- **Transferencias atómicas.** Débito, crédito y ambos asientos se confirman en una sola
  transacción; cualquier error revierte todo. Las cuentas se actualizan siempre en orden de id
  para evitar bloqueos cruzados.
- **Sin filtrar existencia de cuentas.** Una cuenta ajena devuelve `404`, igual que una inexistente.
- **Login uniforme.** Mismo mensaje y tiempo similar con email inexistente o contraseña errónea.

## Requisitos

- JDK 21
- Maven 3.9+

## Puesta en marcha

```bash
export JWT_SECRET="una-cadena-aleatoria-de-al-menos-32-caracteres"
mvn spring-boot:run
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Por defecto usa H2 en archivo (`./data/bank`). Para PostgreSQL:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/bank
export DB_USER=bank
export DB_PASSWORD=bank
```

### Variables de entorno

| Variable                 | Obligatoria | Descripción                                  |
|--------------------------|-------------|----------------------------------------------|
| `JWT_SECRET`             | Sí          | Clave de firma, mínimo 32 caracteres         |
| `JWT_EXPIRATION_MINUTES` | No          | Duración del token (por defecto 30)          |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | No | Base de datos (por defecto H2 en archivo)  |

## Endpoints

| Método | Ruta                              | Descripción                       |
|--------|-----------------------------------|-----------------------------------|
| POST   | `/auth/register`                  | Crear usuario                     |
| POST   | `/auth/login`                     | Obtener token                     |
| GET    | `/auth/me`                        | Usuario autenticado               |
| POST   | `/accounts`                       | Abrir cuenta `{"currency":"USD"}` |
| GET    | `/accounts`                       | Listar mis cuentas                |
| GET    | `/accounts/{number}`              | Detalle y saldo                   |
| POST   | `/accounts/{number}/deposit`      | Depositar `{"amount": 10.00}`     |
| POST   | `/accounts/{number}/withdraw`     | Retirar                           |
| GET    | `/accounts/{number}/transactions` | Historial (`page`, `size`)        |
| POST   | `/transfers`                      | Transferir entre cuentas          |
| GET    | `/health`                         | Estado del servicio               |

### Ejemplo

```bash
curl -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","fullName":"Ana Pérez","password":"supersecreta1"}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"supersecreta1"}' | jq -r .accessToken)

curl -X POST localhost:8080/accounts -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"currency":"USD"}'
```

## Tests

```bash
mvn test
```

Los tests de integración usan H2 en memoria y no requieren configuración.

## Estructura

```
src/main/java/com/bankapi/
  config/       # seguridad y OpenAPI
  security/     # JWT: servicio y filtro
  domain/       # entidades JPA
  repository/   # Spring Data (incluye el UPDATE atómico de saldo)
  service/      # lógica de negocio
  web/          # controladores y manejo de errores
  dto/          # contratos de entrada/salida
  exception/
```

## Limitaciones conocidas

- `ddl-auto: update` es cómodo para desarrollo; en producción usa migraciones (Flyway/Liquibase).
- Los depósitos simulan ingreso de efectivo: no hay pasarela de pago real.
- No incluye límite de intentos de login ni verificación de email.

## Licencia

MIT
