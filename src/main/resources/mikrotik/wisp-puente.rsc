:do { /system scheduler remove [find where name="wisp-puente"] } on-error={}
:do { /system script remove [find where name="wisp-puente"] } on-error={}
/system script add name="wisp-puente" policy=read,write,policy,test,sensitive comment="wisp-gestion: puente de {{RED}} (instala wisp-sync; la web nunca lo modifica)" source="{{SOURCE}}"
:if ([:len [/system script find where name="wisp-puente"]] = 0) do={ :log error "wisp-gestion: no se pudo crear el script wisp-puente" } else={ /system scheduler add name="wisp-puente" interval=2m start-time=startup policy=read,write,policy,test,sensitive on-event="/system script run wisp-puente" comment="wisp-gestion: puente, consulta la web cada 2 min"; :log info "wisp-gestion: puente instalado (script y tarea wisp-puente)" }
