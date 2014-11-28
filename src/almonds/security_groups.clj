;; (ns almonds.security-groups
;;   (:require [amazonica.core :as aws-core :refer [defcredential]]
;;             [amazonica.aws.ec2 :as aws-ec2]
;;             [clojure.core.match :refer [match]]
;;             [dire.core :refer [with-handler!]]
;;             [slingshot.slingshot :refer [throw+]]
;;             [plumbing.core :refer [defnk]]
;;             [schema.core :as s]
;;             [almonds.security-rules :as rules]
;;             [clojure.set :refer [difference]]
;;             [almonds.resource :refer [Resource id retrieve retrieve-raw create delete validate update dependents diff stage apply-diff to-json cf]]))

;; (defn swap-key [m key-to-replace key]
;;   (-> m
;;       (assoc key (key-to-replace m))
;;       (dissoc key-to-replace)))

;; ;; (def group-schema {:group-name s/Str :description s/Str :rules s/Any})
;; (defn sanitize-rules [rs group-name]
;;   (->> (map #(dissoc %1 :user-id-group-pairs) rs)
;;        rules/tr
;;        (map #(assoc % :group-name group-name))
;;        (map rules/map->SecurityRule)
;;        set))

;; (swap-key {:a 2 :b 3} :b :c)

;; (sanitize-rules [{:ip-ranges ["0.0.0.0/0"] :ip-protocol "icmp" :from-port 24 :to-port 22}] "ab")

;; (defn sanitize-group [group]
;;   (-> group
;;       :security-groups
;;       first
;;       (dissoc :tags :ip-permissions-egress :owner-id :group-id)
;;       (swap-key :ip-permissions :rules)))



;; (defrecord SecurityGroup [group-name description rules]
;;   Resource
;;   (id [this]
;;     group-name)
;;   (retrieve-raw [this]
;;     (try
;;       (aws-ec2/describe-security-groups :group-names [group-name])
;;       (catch com.amazonaws.AmazonServiceException e nil)))
;;   (diff [this]
;;     (if-let [gp (retrieve this)]
;;       (when-not (= this gp)
;;         (let [[rules-to-create rules-to-delete] [(difference rules (:rules gp)) (difference (:rules gp) rules)]]
;;           {:to-create rules-to-create :to-update [] :to-delete rules-to-delete}))
;;       {:to-create [this] :to-update [] :to-delete []}))
;;   (update [this]
;;     false)
;;   (create [this]
;;     (aws-ec2/create-security-group :group-name group-name :description description)
;;     (doall
;;      (map create rules))
;;     nil)
;;   (delete [this]
;;     (aws-ec2/delete-security-group :group-name group-name))
;;   (delete? [this] false)
;;   (update? [this] false)
;;   (validate [this]
;;     (every? true?
;;             (map validate (dependents this))))
;;   (retrieve [this]
;;     (when-let [gp (retrieve-raw this)]
;;       (map->SecurityGroup (update-in (sanitize-group gp) [:rules] #(sanitize-rules % group-name)))))
;;   (dependents [this]
;;     (apply vector rules))
;;   (cf [this]
;;     (to-json {group-name {:type "AWS::EC2::SecurityGroup"
;;                           :properties {:group-description description
;;                                        :security-group-ingress (map cf rules)}}})))


;; ;; add to the global cache
;; ;;
;; (def gp3 (->SecurityGroup "mh-test-gp-1"
;;                           "Securityy Group for CADC server"
;;                           (into #{} (map rules/map->SecurityRule
;;                                          #{{:group-name "mh-test-gp-1" :cidr-ip "24.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015}
;;                                            {:group-name "mh-test-gp-1" :cidr-ip "25.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015}}))))


;; (cf gp3)

;; ;;(stage gp3)

;; (comment
;;   (retrieve gp3)

;;   (create gp3)

;;   (map create  (update gp3))

;;   (delete gp3)

;;   (stage gp3)

;;   (diff gp3)

;;   (diff-all)

;;   (apply-diff)
;;   )


;; (with-handler! #'retrieve-raw
;;   com.amazonaws.AmazonServiceException
;;   (constantly nil))

;; (def a (assoc {:cidr-ip "21.0.0.0/0", :ip-protocol "icmp", :from-port 7015, :to-port 7015} :group-name "mh-test-gp-1"))

;; (defnk create-group! [group-name description]
;;   (aws-ec2/create-security-group :group-name group-name :description description))

;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ;;;;;;;;;;;; error-handling



;; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ;; return better errors with just data
;; ;; \do not return false for valid-ports throw an error

;; (def gp1 {:group-name "mh-test-gp-1"
;;           :description "Security Group for CADC server"
;;           :rules #{{:cidr-ip "21.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015}
;;                    {:cidr-ip "26.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015}
;;                    {:cidr-ip "71.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015}
;;                    {:cidr-ip "21.0.0.0/0" :ip-protocol "udp" :from-port 7015 :to-port 7015}}})

;; (def gp2 {:group-name "mh-test-gp-2"
;;           :description "Security Group for PDP server"
;;           :rules #{(rules/ssh-rule "22.0.0.0/0")
;;                    rules/icmp-rule}})





;; ;; sync list of entities -
;; ;; sync each entity
;; ;; if server has additional entities safely remove them

;; ;; protocol
;; ;; create, delete, safe to delete, equality, update, sync list, identity, adding-key (fn of identity)

;; ;; ability to define infra as data
;; ;; each entity has a type and dispatch takes place on the type.
;; ;; create a new record for that type and start execution
;; ;;

;; ;; a global list
;; ;; read from data or just add to the atom.
;; ;; based on type check will occur and will add it
;; ;; add-resource - to add resources to the global list
;; ;; sync-lists

;; ;; plan would show the diffs
;; ;; execute applies the diffs

;; ;; functional infrastructure
;; ;; a list of atoms - each atom contains a record implementing the protocol
;; ;; based on each atom retrieve the latest state for those entities
;; ;; show which all
