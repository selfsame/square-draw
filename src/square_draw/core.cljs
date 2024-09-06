(ns ^:figwheel-hooks square-draw.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defonce state (r/atom {
  :tool :square
  :color "#e3357b"
  :file []
  :selected nil}))

(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "2d"))

(defn render! []
  (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))
  (dorun
    (for [el (:file @state)]
      (cond 
        (= (:shape el) :square)
        (do 
          (set! (.-fillStyle ctx) (:fill el))
          (.fillRect ctx (:x el) (:y el) (:w el) (:h el)))
        (= (:shape el) :circle)
        (let [w (js/Math.abs (:w el))
              h (js/Math.abs (:h el))] 
          (set! (.-fillStyle ctx) (:fill el))
          (.beginPath ctx)
          (.ellipse ctx 
            (+ (:x el) (* w 0.5)) (+ (:y el) (* h 0.5)) 
            (* w 0.5) (* h 0.5) 0 0 (* js/Math.PI 2))
          (.fill ctx)
          ))))
  (when-let [el (get (:file @state) (:selected @state))]
    (.setLineDash ctx #js [6])
    (.strokeRect ctx (:x el) (:y el) (:w el) (:h el))))

; ideally this would schedule render! in a requestAnimationFrame
(add-watch state :render
  (fn [k r o n]
    (when (or (not= (:file o) (:file n))
              (not= (:selected o) (:selected n)))
      (render!))))



(defn mouse->canvas [x y]
  ; hardcoded in the css
  [(- x 100) (- y 12)])

(defn alter-selected [m]
  (when-let [idx (:selected @state)]
    (swap! state update-in [:file idx] merge m)))

(defn clean-selected []
  ; canvas.fillRect can take a negative width or height but it causes
  ; problems for our picker so correct it here
  (let [sel (get-in @state [:file (:selected @state)])]
    (if (neg? (:w sel))
      (alter-selected {:w (* (:w sel) -1) :x (+ (:x sel) (:w sel))}))
    (if (neg? (:h sel))
      (alter-selected {:h (* (:h sel) -1) :y (+ (:y sel) (:h sel))}))))

(defn pick [cx cy]
  ; return a vector of every element overlapping the given coords
  (let [[cx cy] (mouse->canvas cx cy)]
    (filterv 
      (fn [[idx {:keys [x y w h]}]]
        (and (< x cx (+ x w))
             (< y cy (+ y h))))
      (reverse (map-indexed vector (:file @state))))))

(defn check-resize [mx my]
  ; return keywords for which hotspot of the selected element is hovered over
  (let [[mx my] (mouse->canvas mx my)]
    (when-let [{:keys [x y w h]} (get-in @state [:file (:selected @state)])]
      (when (and (< (- x 4) mx (+ x w 4))
                 (< (- y 4) my (+ y h 4)))
        (let [kws (set [
                    (if (< mx (+ x 4)) :left)
                    (if (> mx (+ w x -4)) :right)
                    (if (< my (+ y 4)) :top)
                    (if (> my (+ h y -4)) :bottom)])
              kws (disj kws nil)]
          (when-not (empty? kws) kws))))))

(defn on-draw-down [e shape]
  ; note the initial mouse x/y and create a new element in the file vector
  ; then set up a mouse move fn that modifies this new element's width based on the current mouse position
  (let [[x y] (mouse->canvas (.-x e) (.-y e))
        idx (count (:file @state))]
    (swap! state update :file conj {:shape shape :x x :y y :w 0 :h 0 :fill (:color @state)})
    (swap! state assoc :selected idx)
    (swap! state assoc :movefn 
      (fn [e]
        (let [[mx my] (mouse->canvas (.-x e) (.-y e))]
          (alter-selected {:w (- mx x) :h (- my y)}))))
    (swap! state assoc :upfn 
      (fn [e]
        (clean-selected)
        (swap! state assoc :selected nil)))))

(defn on-edit-down [e]
  (let [[x y] [(.-x e) (.-y e)]]
    (if-let [resize (check-resize x y)]
      (let [picked (get-in @state [:file (:selected @state)])]
        (swap! state assoc :movefn 
            (fn [e]
              (let [[nx ny] [(.-x e) (.-y e)]
                    [dx dy] [(- nx x) (- ny y)]]
                (if (:left resize)
                  (alter-selected {:x (+ (:x picked) dx)
                                   :w (- (:w picked) dx)}))
                (if (:right resize)
                  (alter-selected {:w (+ (:w picked) dx)}))
                (if (:top resize)
                  (alter-selected {:y (+ (:y picked) dy)
                                   :h (- (:h picked) dy)}))
                (if (:bottom resize)
                  (alter-selected {:h (+ (:h picked) dy)})))))
        (swap! state assoc :upfn (fn [e] (clean-selected))))
      (if-let [[idx picked] (first (pick (.-x e) (.-y e)))]
        (do 
          (swap! state assoc :selected idx)
          (swap! state assoc :color (:fill picked))
          (swap! state assoc :movefn 
            ; calculate the delta mouse x/y from the start of the move then add that to the inital
            ; picked element's position
            (fn [e]
              (let [[nx ny] [(.-x e) (.-y e)]]
                (alter-selected {:x (+ (:x picked) (- nx x)) 
                                 :y (+ (:y picked) (- ny y))})))))
        (swap! state assoc :selected nil)))))

; Input cycles, dispatch on mouse down based on the current tool.  If move/up handlers are needed
; they can be set up in the dispatched fn

(defn pointer-down [e]
  (if (= (.-target e) canvas)
    (cond
      (#{:square :circle} (:tool @state)) (on-draw-down e (:tool @state))
      (= (:tool @state) :edit) (on-edit-down e)))
  (if (= (.-target e) (.. js/document -body -parentElement))
    (swap! state assoc :selected nil)))

(defn pointer-move [e]
  ; check which cursor to display
  (cond
    (#{:square :circle} (:tool @state))
    (set! (.. canvas -style -cursor) "crosshair")
    (= (:tool @state) :edit)
    (let [[idx picked] (first (pick (.-x e) (.-y e)))
          resize (check-resize (.-x e) (.-y e))]
      (cond
        (#{#{:left} #{:right}} resize)
        (set! (.. canvas -style -cursor) "ew-resize")

        (#{#{:top} #{:bottom}} resize)
        (set! (.. canvas -style -cursor) "ns-resize")

        (#{#{:top :left} #{:bottom :right}} resize)
        (set! (.. canvas -style -cursor) "nwse-resize")

        (#{#{:bottom :left} #{:top :right}} resize)
        (set! (.. canvas -style -cursor) "nesw-resize")

        (= idx (:selected @state))
        (set! (.. canvas -style -cursor) "move")

        picked
        (set! (.. canvas -style -cursor) "pointer")
        
        :else
        (set! (.. canvas -style -cursor) "default"))))

  (if (:movefn @state) ((:movefn @state) e)))

(defn pointer-up [e]
  (if (:upfn @state) ((:upfn @state) e))
  (swap! state dissoc :movefn :upfn))

(js/addEventListener "pointerdown" pointer-down)
(js/addEventListener "pointermove" pointer-move)
(js/addEventListener "pointerup"   pointer-up)


; UI

(defn toolbar-ui []
  [:div.toolbar
    [:div.title "tools"]
    (into [:div]
      (map 
        (fn [k src] 
          [:div.tool 
            {:class (if (= (:tool @state) k) "active")
             :on-click (fn [e] 
              (swap! state assoc :tool k))}
            [:img {:src src}]
            k])
        [:edit :square :circle]
        ["img/edit.png" "img/square.png" "img/circle.png"]))
    [:label "fillcolor"]
    [:input {
      :type :color 
      :value (:color @state)
      :on-change (fn [e] 
        (swap! state assoc :color (.. e -target -value))
        (when (:selected @state)
          (alter-selected {:fill (:color @state)})))}]
    (when-let [idx (:selected @state)]
      [:button {
        :on-click (fn [e] 
          (swap! state assoc 
            :file (vec (concat 
                    (subvec (:file @state) 0 idx)
                    (subvec (:file @state) (inc idx))))
            :selected nil))} 
       "delete"])])

(defn root-ui []
  [:div
    [toolbar-ui]])

(rd/render [root-ui] (js/document.querySelector "#app"))

(render!)