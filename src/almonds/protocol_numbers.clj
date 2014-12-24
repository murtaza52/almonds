(ns almonds.protocol-numbers)

(def protocol-numbers 
  {:all "-1"
   :icmp "1"
   :igmp "2"
   :tcp "6"
   :igp "9"
   :udp "17"
   :rdp "27"
   :dgp "86"
   :l2tp "115"
   :smp "121"})

(defn get-protocol-num [protocol]
  (if (keyword? protocol)
    (protocol protocol-numbers)
    protocol))

(defn get-protocol-keyword [protocol]
  (first (filter (comp #{protocol} protocol-numbers) (keys protocol-numbers))))
