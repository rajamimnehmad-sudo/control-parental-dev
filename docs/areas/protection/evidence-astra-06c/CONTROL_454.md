# DEV454 controlled sample limitations

First fixture navigation after installation had15 decoded original-dimension resources, first decoded resource end1803.6ms and first eight completion3507.4ms. It is not an isolated cold benchmark: Chrome initially restored Fravega and its image analyses were still active before the fixture navigation. Runtime454 first four Fravega image decisions show645/561/579/546ms inference and growing body wait; do not attribute this mixed workload solely to fixture cold latency.

Two later same-session fixture navigations: first decoded resource ends305.1/286.3ms, first eight completion960.2/863.7ms, page load1043.8/980.0ms. These are resource-delivery/document events, not first paint, exact above-fold completeness or ON/OFF deltas. Network/cache conditions differ from the initial mixed workload.

SVG16/16, forms13/13 and Shadow12/12 passed on exact454. Existing unsupported local raster/srcdoc/target contracts remain unsupported rather than declared compatible. No cache/data wipe, model change or unsafe release was used.
