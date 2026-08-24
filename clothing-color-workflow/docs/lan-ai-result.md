# LAN canvas result storage

For a Windows local/LAN development deployment, keep the following settings in
`backend/src/main/resources/application-dev.yml` (environment configuration is
intentionally not tracked because it contains deployment credentials):

```yaml
app:
  local-save-root: D:/AiResult
  kie:
    callback-url: ""
```

After restarting the service, completed canvas tasks are written under
`D:\AiResult\canvas\` and returned to browsers as `/ai-result/canvas/...`.
Clients must use the HTTP URL of the LAN server; a browser must never be given
the server's `D:` path directly.

The `prod` profile deliberately does not use this directory for canvas KIE
results. It promotes each temporary KIE URL to the permanent OSS result bucket
before returning it to the browser. If a public callback is used in prod, it
must route to that exact production deployment.
