(ns almonds.security-rules
  (:require [amazonica.core :as aws-core :refer [defcredential]]
            [amazonica.aws.ec2 :as aws-ec2]
            [clojure.core.match :refer [match]]
            [dire.core :refer [with-handler!]]
            [slingshot.slingshot :refer [throw+]]
            [plumbing.core :refer [defnk]]
            [schema.core :as s]
            [almonds.resource :refer [Resource create delete id validate validate-all to-json cf]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; validations ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def schema {:group-name s/Str :cidr-ip s/Str :ip-protocol (s/enum "tcp" "udp" "icmp") :from-port s/Int :to-port s/Int})

(defn valid-schema? [rule]
  (when (s/validate schema rule)) true)

(defnk valid-ports? [from-port to-port]
  (= from-port to-port))


((validate-all valid-schema? valid-ports?) {:group-name "abc" :cidr-ip "0.0.0.0/0" :ip-protocol "icmp" :from-port 23 :to-port 23})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord SecurityRule [group-name cidr-ip ip-protocol from-port to-port]
  Resource
  (create [this]
          (aws-ec2/authorize-security-group-ingress
           :group-name group-name
           :cidr-ip cidr-ip
           :ip-protocol ip-protocol
           :from-port from-port
           :to-port to-port))
  (id [this ] this)
  (delete? [this] true)
  (update? [this] false)
  (delete [this]
          (aws-ec2/revoke-security-group-ingress
           :group-name group-name
           :cidr-ip cidr-ip
           :ip-protocol ip-protocol
           :from-port from-port
           :to-port to-port))
  (validate [this]
            ((validate-all valid-schema? valid-ports?) this))
  (cf [this]
    (to-json {:ip-protocol ip-protocol
              :cidr-ip cidr-ip
              :to-port to-port
              :from-port from-port})))

(cf
  (map->SecurityRule {:cidr-ip "21.0.0.0/0" :ip-protocol "tcp" :from-port 22 :to-port 22 :group-name "mh-test-gp-1"}))

(map->SecurityRule {:cidr-ip "21.0.0.0/0" :ip-protocol "tcp" :from-port 22 :to-port 22 :group-name "mh-test-gp-1"})

;; (delete
;;  (map->SecurityRule {:group-name "mh-test-gp-1", :cidr-ip "27.0.0.0/0", :ip-protocol "tcp", :from-port 7015, :to-port 7015}))

;; (create (map->SecurityRule {:group-name "mh-test-gp-1", :cidr-ip "27.0.0.0/0", :ip-protocol "tcp", :from-port 7015, :to-port 7015}))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; credentials ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defcredential "AKIAIUUO5FORJVPC7CAA" "Hds3ASivgecTXCcugjc3hlL2yb2gr3UPuyrjQ93g" "https://ec2.amazonaws.com")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; AWS SDK ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(with-handler! #'create
  com.amazonaws.AmazonServiceException
  (fn [e group-name rule] (throw+ {:group-name group-name :rule rule :msg (.getMessage e)})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; rules ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ssh-rule #(hash-map :cidr-ip % :ip-protocol "tcp" :from-port 22 :to-port 22))

(def icmp-rule {:cidr-ip "0.0.0.0/0" :ip-protocol "icmp" :from-port 23 :to-port 23})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; utility fns ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def rules-match?
  #(when (= (dissoc %1 :cidr-ip :ip-ranges)
            (dissoc %2 :cidr-ip :ip-ranges))
     %2))

(defnk transform-r [cidr-ip ip-protocol from-port to-port]
                   (zipmap [:ip-ranges :ip-protocol :from-port :to-port] [[cidr-ip] ip-protocol from-port to-port]))

(defn transform-rules [rules]
  (reduce
    (fn [transformed-rules rule]
      (if-let [matched-rule (some (partial rules-match? rule) transformed-rules)]
        (conj (remove #{matched-rule} transformed-rules)
              (update-in matched-rule [:ip-ranges] conj (rule :cidr-ip)))
        (conj transformed-rules (transform-r rule))))
   (seq ())
   rules))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; sample data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ru1 {:ip-ranges ["0.0.0.0/0"] :ip-protocol "icmp" :from-port 24 :to-port 22})

(def r1 {:cidr-ip "21.0.0.0/0" :ip-protocol "icmp" :from-port 24 :to-port 22})

(rules-match? ru1 r1)

(def ru
  [{:ip-ranges ["21.0.0.0/0" "22.0.0.0/0"] :ip-protocol "udp" :from-port 22 :to-port 22}
   {:ip-ranges ["21.0.0.0/0"] :ip-protocol "tcp" :from-port 22 :to-port 22}])

(defn tr [rules]
  (for [r rules
        ip (:ip-ranges r)]
    (-> r
        (assoc :cidr-ip ip)
        (dissoc :ip-ranges))))

(def rus
  [{:cidr-ip "21.0.0.0/0" :ip-protocol "udp" :from-port 22 :to-port 22}
   {:cidr-ip "22.0.0.0/0" :ip-protocol "udp" :from-port 22 :to-port 22}
   {:cidr-ip "21.0.0.0/0" :ip-protocol "tcp" :from-port 22 :to-port 22}])

(def ru2 {:cidr-ip "23.0.0.0/0" :ip-protocol "udp" :from-port 22 :to-port 22})

(def ru3 {:cidr-ip "23.0.0.0/0" :ip-protocol "udp" :from-port 23 :to-port 22})

;;(create (map->SecurityRule ru3))

(transform-rules rus)

(transform-r {:cidr-ip "21.0.0.0/0" :ip-protocol "udp" :from-port 23 :to-port 22})

(valid-ports? r1)

(valid-schema? {:group-name "hi" :cidr-ip "21.0.0.0/0" :ip-protocol "tcp" :from-port 7015 :to-port 7015})
