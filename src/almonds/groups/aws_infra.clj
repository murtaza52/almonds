(ns almonds.groups.almonds
    "Node defintions for almonds"
    (:require
     [pallet.api :refer [group-spec server-spec node-spec plan-fn]]
     [pallet.crate.automated-admin-user :refer [automated-admin-user]]))

;;(def aws (pallet.configure/compute-service :faiz-aws))

(def base-image-spec
          {:image-id "us-east-1/ami-3c994355"
           :os-family :amzn-linux
           :os-version "2013.09"
           :login-user "ec2-user"})

(def conf {:location-id "us-east-1d"
           :ssh-key "mykey"
           :instance-type "t1.micro"
           :image-spec base-image-spec})

(defn create-node-spec [conf]
  (node-spec
   :image (conf :image-spec)
   :location {:location-id (conf :location-id)}
   :provider {:pallet-ec2 {:key-name (conf :ssh-key)}}
   :hardware {:hardware-id (conf :instance-type)}))

(def cadc-node-spec (merge conf {:instance-type "t1-large"}))

(def base-server
    (server-spec
     :phases
     {:bootstrap (plan-fn (automated-admin-user))}))

;; (def
;;   ^{:doc "Define a server spec for almonds"}
;;   almonds-server
;;   (server-spec
;;    :phases
;;    {:configure (plan-fn
;;                  ;; Add your crate class here
;;                  )}))

;; (def
;;   ^{:doc "Defines a group spec that can be passed to converge or lift."}
;;   almonds
;;   (group-spec
;;    "almonds"
;;    :extends [base-server almonds-server]
;;    :node-spec default-node-spec))
