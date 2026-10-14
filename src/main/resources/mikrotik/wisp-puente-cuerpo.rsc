:local res ""
:local ok true
:do {
  :set res ([/tool fetch url="{{URL}}/api/mikrotik/bootstrap" http-header-field="X-Red-Token: {{TOKEN}}" check-certificate=yes-without-crl output=user as-value]->"data")
} on-error={ :set ok false }
:if (!$ok) do={
  :log warning "wisp-puente: no se pudo consultar la web (internet, certificado o token)"
} else={
  :local marca "{{MARCADOR}}\n"
  :if (([:len $res] > [:len $marca]) && ([:pick $res 0 [:len $marca]] = $marca)) do={
    :log info "wisp-puente: la web envio una instalacion, se ejecuta"
    :do {
      :local codigo [:parse $res]
      $codigo
    } on-error={ :log error "wisp-puente: fallo la instalacion recibida" }
  }
}
