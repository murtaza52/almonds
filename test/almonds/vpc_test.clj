;; (ns almonds.vpc-test
;;   (:require [midje.sweet :refer [facts fact]]
;;             [almonds.subnet :as s]
;;             [almonds.resource :as r]
;;             [almonds.vpc :as vpc]
;;             [almonds.customer-gateway :as cg]))

;; (def v  (vpc/map->VPC {:almonds-tags "my-vpc"}))

;; (def v2  (vpc/map->VPC {:almonds-tags "my-vpc2"}))

;; (r/stage :central-vpc v)
;; (r/stage :central-vpc v2)

;; ;; (r/get-almonds-resources :central )

;; (r/retrieve-all (vpc/map->VPC {}))

;; ;; lets get all resources for a certain type for the stack.
;; ;; add stack-id also
;; ;; do a diff between them

;; ;;(reset! r/stage-state {})


;; ;;(conj {:a 2} {:a 3} )
