(ns almonds.state)

(def local-state (atom {}))
(def remote-state (atom {}))
(def remote-state-all (atom {}))
(def resource-types (atom []))

(def already-retrieved-remote? (atom false))

;;;;;;;;;;;;;;; clear states ;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-all []
  (reset! already-retrieved-remote? false)
  (doseq [state [local-state remote-state remote-state-all]]
    (reset! state {})))

(defn clear-remote-state []
  (reset! remote-state-all {})
  (reset! remote-state {}))

(def reset-resource-types #(reset! resource-types []))

(def set-already-retrieved-remote #(reset! already-retrieved-remote? true))
