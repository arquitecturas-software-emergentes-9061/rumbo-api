# Demo en vivo: el modelo corre en tu computadora

El API publicado en Render (plan gratuito, 512 MB) no tiene memoria para el modelo de planos: el mapper
estructural con CubiCasa5k llega a unos 1,7 GB al analizar un plano. Para una demo, el mapper corre en la
computadora del presentador con Docker y se publica con un túnel temporal de Cloudflare; el API de Render lo
usa a través de `STRUCTURAL_MAPPER_URL`.

```
Web (Vercel) ──► API en Render ──► túnel https://*.trycloudflare.com ──► mapper en Docker (tu computadora)
```

## Requisitos

- Docker Desktop abierto, con **al menos 4 GB** de memoria para contenedores (Settings → Resources).
- Este repositorio clonado. Funciona en Mac con Apple Silicon o Intel, Linux y Windows con WSL.
- No hace falta cuenta de Cloudflare: el túnel rápido es gratuito y anónimo.

## Un día antes

Construye la imagen para no esperar en clase (descarga el modelo de 66 MB y verifica su SHA-256):

```bash
docker compose build structural-mapper
```

## Durante la demo

1. Arranca el mapper y el túnel:

   ```bash
   scripts/live-mapper.sh
   ```

   Al final muestra una dirección como `https://ejemplo-aleatorio.trycloudflare.com`, después de comprobar que
   el modelo responde a través del túnel.

2. En Render abre **rumbo-api-demo → Environment**, define `STRUCTURAL_MAPPER_URL` con esa dirección (sin barra
   final) y elige **Save and deploy**. El API tarda uno o dos minutos en reiniciar.

3. En la web, abre **Editor de mapa**, elige un plano PDF, PNG o JPG y pulsa **Generar modelo**.

Mantén la computadora encendida, sin suspenderse y conectada mientras dure la demo.

## Al terminar

```bash
scripts/live-mapper.sh stop
```

La dirección del túnel deja de existir. El API vuelve a dejar los planos como vista previa hasta la próxima demo.

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| «Este plano solo permite vista previa» | El API no llega al mapper: revisa que `STRUCTURAL_MAPPER_URL` sea la dirección actual y que el despliegue terminó. |
| El script no muestra el modelo | Docker tiene poca memoria o el build falló: `docker compose logs structural-mapper`. |
| Funcionaba y dejó de responder | La computadora se suspendió o cambió de red: vuelve a correr el script; la dirección cambia en cada arranque. |
| La primera llamada tarda | El API de Render se duerme tras un rato sin uso; la primera petición lo despierta. |

## Notas

- La dirección del túnel es pública mientras está abierta: cualquiera que la conozca puede enviar planos al
  mapper. Ciérrala al terminar.
- Render despliega la rama `deploy`; `render.yaml` declara `STRUCTURAL_MAPPER_URL` con `sync: false` para que el
  valor del panel no se pierda al sincronizar el Blueprint.
- El servicio `rumbo-structural-mapper-j69m` que se creó en Render no puede analizar planos con 512 MB y ya no se
  usa; se puede eliminar desde el panel.
