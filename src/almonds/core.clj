(ns almonds.core
  (:require [almonds.api :as api]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; reset state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(api/clear-all)

;;;;;;;;;;;;;;;; execute the handler calls ;;;;;;;;;;;;;;;
(require 'almonds.handler)

;;;;;;;;;;;;;;;; reset resources ;;;;;;;;;;;;;;;;;;;;;;
(require 'almonds.resources :reload)

;;;;;;;;;;;;;;; compile other resources ;;;;;;;;;;;;;;;;;;

(require 'almonds.resources.security-group 
         'almonds.resources.eip-assoc
         'almonds.resources.elastic-ip
         'almonds.resources.instance
         'almonds.resources.security-rule)
