:local url "{{URL}}/api/mikrotik"
:local hdr "X-Red-Token: {{TOKEN}}"
:local hdrPost "X-Red-Token: {{TOKEN}},Content-Type: text/plain"
:if ([:len [/system script job find where script="wisp-sync"]] > 1) do={
  :log warning "wisp-sync: la ejecucion anterior sigue en curso"
} else={
  :local res ""
  :local okConsulta true
  :do {
    :set res ([/tool fetch url=($url . "/acciones") http-header-field=$hdr check-certificate=yes-without-crl output=user as-value]->"data")
  } on-error={ :set okConsulta false }
  :if (!$okConsulta) do={
    :log warning "wisp-sync: no se pudo consultar la web (internet, certificado o token)"
  } else={
    :if (([:pick $res 0 14] != "# wisp-sync v1") || ([:typeof [:find $res "\n# fin\n"]] = "nil")) do={
      :log warning "wisp-sync: respuesta incompleta o desconocida, se ignora"
    } else={
      :local padre ""
      :local protegidas [:toarray ""]
      :local repNombres [:toarray ""]
      :local repIps [:toarray ""]
      :local resto $res
      :while ([:len $resto] > 0) do={
        :local linea $resto
        :local finLinea [:find $resto "\n"]
        :if ([:typeof $finLinea] = "nil") do={
          :set resto ""
        } else={
          :set linea [:pick $resto 0 $finLinea]
          :set resto [:pick $resto ($finLinea + 1) [:len $resto]]
        }
        :local f [:toarray ""]
        :local campos $linea
        :local seguir true
        :while ($seguir) do={
          :local sep [:find $campos "|"]
          :if ([:typeof $sep] = "nil") do={
            :set ($f->[:len $f]) $campos
            :set seguir false
          } else={
            :set ($f->[:len $f]) [:pick $campos 0 $sep]
            :set campos [:pick $campos ($sep + 1) [:len $campos]]
          }
        }
        :local tipo ($f->0)
        :if ($tipo = "P") do={ :set padre ($f->1) }
        :if ($tipo = "X") do={ :set ($protegidas->[:len $protegidas]) ($f->1) }
        :if ($tipo = "R") do={
          :set ($repNombres->[:len $repNombres]) ($f->1)
          :set ($repIps->[:len $repIps]) ($f->2)
        }
        :if ($tipo = "Q") do={
          :local id ($f->1)
          :local n ($f->2)
          :local err ""
          :if (($n = "") || ($padre = "") || ($n = $padre)) do={ :set err "Nombre de cola no permitido" }
          :foreach p in=$protegidas do={ :if ($p = $n) do={ :set err "Cola protegida: no se modifica" } }
          :if (($err = "") && (($f->5) != $padre)) do={ :set err "El parent recibido no es la cola padre" }
          :if (($err = "") && ([:len [/queue simple find where name=$padre]] = 0)) do={ :set err ("No existe la cola padre " . $padre) }
          :if ($err = "") do={
            :local tipos ($f->6)
            :local barra [:find $tipos "/"]
            :local tipoSubida $tipos
            :local tipoBajada $tipos
            :if ([:typeof $barra] != "nil") do={
              :set tipoSubida [:pick $tipos 0 $barra]
              :set tipoBajada [:pick $tipos ($barra + 1) [:len $tipos]]
            }
            :if ([:len [/queue type find where name=$tipoSubida]] = 0) do={ :set err ("No existe el tipo de cola " . $tipoSubida) }
            :if ([:len [/queue type find where name=$tipoBajada]] = 0) do={ :set err ("No existe el tipo de cola " . $tipoBajada) }
          }
          :if ($err = "") do={
            :local q [/queue simple find where name=$n]
            :local ok true
            :do {
              :if ([:len $q] = 0) do={
                /queue simple add name=$n target=($f->3) max-limit=($f->4) parent=$padre queue=($f->6) disabled=(($f->7) = "yes") comment=($f->8)
              } else={
                /queue simple set ($q->0) target=($f->3) max-limit=($f->4) parent=$padre queue=($f->6) disabled=(($f->7) = "yes") comment=($f->8)
              }
            } on-error={ :set ok false }
            :if (!$ok) do={ :set err "RouterOS rechazo el cambio (revisa el target, la velocidad y que la IP no este en otra cola)" }
          }
          :do {
            :if ($err = "") do={
              /tool fetch url=($url . "/acciones/" . $id . "/aplicada") http-method=post http-header-field=$hdrPost http-data="" check-certificate=yes-without-crl output=none
            } else={
              :log warning ("wisp-sync: cola " . $n . ": " . $err)
              /tool fetch url=($url . "/acciones/" . $id . "/error") http-method=post http-header-field=$hdrPost http-data=$err check-certificate=yes-without-crl output=none
            }
          } on-error={ :log warning "wisp-sync: no se pudo confirmar una accion" }
        }
        :if ($tipo = "C") do={
          :local id ($f->1)
          :local txt ""
          :local okTxt true
          :do {
            :set txt ([/tool fetch url=($url . "/comandos/" . $id) http-header-field=$hdr check-certificate=yes-without-crl output=user as-value]->"data")
          } on-error={ :set okTxt false }
          :if ($okTxt) do={
            :log info ("wisp-sync: ejecutando comando " . $id . " de la terminal remota")
            :local salida ""
            :local okCmd true
            :do { :set salida [:execute script=$txt as-string] } on-error={ :set okCmd false }
            :if ([:len $salida] > 16000) do={ :set salida [:pick $salida 0 16000] }
            :do {
              :if ($okCmd) do={
                /tool fetch url=($url . "/comandos/" . $id . "/salida") http-method=post http-header-field=$hdrPost http-data=$salida check-certificate=yes-without-crl output=none
              } else={
                /tool fetch url=($url . "/comandos/" . $id . "/error") http-method=post http-header-field=$hdrPost http-data="RouterOS no pudo ejecutar el comando (revisa la sintaxis)" check-certificate=yes-without-crl output=none
              }
            } on-error={ :log warning "wisp-sync: no se pudo devolver la salida de un comando" }
          }
        }
      }
      :local cuerpo ""
      :local total [:len $repNombres]
      :if ($total > 0) do={
        :for i from=0 to=($total - 1) do={
          :local n ($repNombres->$i)
          :local ip ($repIps->$i)
          :local ping ""
          :if ($ip != "") do={
            :do {
              :if ([/ping address=$ip count=1] > 0) do={ :set ping "1" } else={ :set ping "0" }
            } on-error={}
          }
          :local q [/queue simple find where name=$n]
          :if ([:len $q] = 0) do={
            :set cuerpo ($cuerpo . "Q|" . $n . "|0||||||||" . $ping . "|\n")
          } else={
            :local d [/queue simple get ($q->0)]
            :set cuerpo ($cuerpo . "Q|" . $n . "|1|" . [:tostr ($d->"target")] . "|" . ($d->"max-limit") . "|" . ($d->"parent") . "|" . ($d->"queue") . "|" . [:tostr ($d->"disabled")] . "|" . ($d->"rate") . "|" . ($d->"bytes") . "|" . $ping . "|" . ($d->"comment") . "\n")
          }
        }
      }
      :do {
        /tool fetch url=($url . "/reporte") http-method=post http-header-field=$hdrPost http-data=$cuerpo check-certificate=yes-without-crl output=none
      } on-error={ :log warning "wisp-sync: no se pudo enviar el reporte" }
    }
  }
}
