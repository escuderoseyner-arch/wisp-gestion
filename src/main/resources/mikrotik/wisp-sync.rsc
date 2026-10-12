# wisp-gestion: sincronizacion de colas para la red {{RED}}
# Instala (o reinstala) SOLO el script y la tarea programada "wisp-sync".
# No toca el script puente ni sus tareas, ni el firewall, ni la cola padre, ni otras colas.
# Cada comando va en una sola linea (como /export): se puede pegar en la terminal o usar con /import.
:do { /system scheduler remove [find where name="wisp-sync"] } on-error={}
:do { /system script remove [find where name="wisp-sync"] } on-error={}
/system script add name="wisp-sync" policy=read,write,policy,test,sensitive comment="wisp-gestion: sincronizacion de colas (independiente del puente)" source="{{SOURCE}}"
:if ([:len [/system script find where name="wisp-sync"]] = 0) do={ :log error "wisp-gestion: no se pudo crear el script wisp-sync, no se crea la tarea" } else={ /system scheduler add name="wisp-sync" interval={{INTERVALO}}s start-time=startup policy=read,write,policy,test,sensitive on-event="/system script run wisp-sync" comment="wisp-gestion: consulta la web cada {{INTERVALO}} s"; :log info "wisp-gestion: sincronizacion instalada (script y tarea wisp-sync)" }
