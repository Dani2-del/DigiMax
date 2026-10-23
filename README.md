# DigiMAX

Monorepo for the Digitrans Spring Boot modules:

- `Acceso`: accounts and authentication.
- `Transporte`: the active SITM application.
- `Billetera`: universal wallet shared across services.
- `RecargasMovil`: simulated mobile recharge catalog and purchases.
- `Correo`: internal outbound email service.

## Local MongoDB databases

The modules use separate local MongoDB databases by default:

| Module | Environment variable | Default URI |
| --- | --- | --- |
| Acceso | `ACCESO_MONGODB_URI` | `mongodb://localhost:27017/digitrans_acceso` |
| Transporte | `TRANSPORTE_MONGODB_URI` | `mongodb://localhost:27017/digitrans_transporte` |
| RecargasMovil | `RECARGAS_MONGODB_URI` | `mongodb://localhost:27017/digitrans_recargas` |
| Billetera | `BILLETERA_MONGODB_URI` | `mongodb://localhost:27017/digitrans_billetera` |

`Transporte` retains its own transit cards; the universal card is a separate wallet whose balance is shared with RecargasMovil and can also be used for transit fares. All wallet top-ups, mobile purchases, and universal-card transit charges are simulated; there are no real payment or mobile-operator integrations. The recharge catalog starts empty and can be configured by users with the existing ADMIN or MODERATOR roles. `Correo` does not need a database. MongoDB creates each database when the module first writes a document. The old local `Digitrans_Max` database is left untouched; no data is migrated or deleted.

These defaults use `localhost` and do not point to MongoDB Atlas. Do not set these local development variables to an Atlas URI.

## Local secrets

Keep `APP_JWT_SECRET`, `MAIL_SERVICE_API_KEY`, and Correo's `SMTP_USERNAME` and `SMTP_PASSWORD` in the local environment, never in source files. `MAIL_SERVICE_API_KEY` must match in Correo, Acceso, and Transporte. The root `.gitignore` excludes local environment files and generated build output.
`BILLETERA_PROVISIONING_KEY` may be set to a separate shared secret for account-to-wallet provisioning; when omitted, the modules use `APP_JWT_SECRET` for that internal request.

Run the active services with `Iniciar-Digitrans.bat` after configuring the required environment variables.
