(ns almonds.resources.instance
  (:require [amazonica.aws.ec2 :as aws-ec2]
            [almonds.utils :refer :all]
            [almonds.contract :refer :all]
            [almonds.api :refer :all]
            [almonds.state :refer :all]))

(defmethod validate :instance [m]
  true)

(defn prepare-request [m]
  (as-> m m
    (update-in m [:security-group-ids] #(map aws-id %))
    (merge {:min-count 1} m)
    (merge {:max-count 1} m)
    (dissoc m :almonds-tags :almonds-type)))

(defmethod create :instance [m]
  (let [request (prepare-request m)
        response (aws-ec2/run-instances request)
        id (-> response :reservation :instances first :instance-id)]
    (create-aws-tags id m)))

(defmethod parent-type :instance [_]
  nil)

(defmethod sanitize :instance [m]
  (-> m
      (update-in [:security-groups] 
                 (fn[groups]
                   (map #(-> % :group-id aws-id->almonds-tags) groups)))))

(defmethod retrieve-all :instance [_]
  (->> (aws-ec2/describe-instances)
       vals
       first
       (mapcat :instances)))

(comment (retrieve-all {:almonds-type :instance}))

(defmethod delete :instance [m]
  (aws-ec2/terminate-instances {:instance-ids [(aws-id (:almonds-tags m))]}))

(defmethod aws-id-key :instance [_]
  :instance-id)

(defmethod pre-staging :instance [m]
  (as-> m m
    (update-in m
               [:security-group-ids]
               (fn[groups]
                 (map #(add-type :security-group %) groups)))))

(defmethod get-default-dependents :instance [m]
  [])

(defmethod is-dependent? :instance [_]
  false)

(defmethod dependent-types :instance [_]
  [])

(defmethod dependents :instance [m]
  [])
