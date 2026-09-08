"""Summarize independent logged phase samples; never add overlapping intervals."""
import collections, json, math, re, sys
samples=collections.defaultdict(list)
for line in open(sys.argv[1]):
    for key,value in re.findall(r'\b(admissionWaitMs|tlsMaterialMs|tlsCacheWaitMs|tlsCreationMs|bodyAdmissionMs|bodyReadMs|hashMs|inferenceQueueMs|upstreamHeadersMs|documentDecisionMs|sanitizeMs|decodeMs|inferenceMs|localDecisionMs|downstreamWriteMs|requestToDeliveryMs)=([0-9.]+)',line):
        group='image' if 'requestToDeliveryMs=' in line else ('document' if 'phase=media_shield_document' in line else 'other')
        if key in ('admissionWaitMs','tlsMaterialMs','tlsCacheWaitMs','tlsCreationMs'):group='connection'
        samples[group+'/'+key].append(float(value))
        if group=='image':
            source=re.search(r'\bsource=([^ ]+)',line)
            if source:samples['image_'+source[1]+'/'+key].append(float(value))
        if 'phase=tls_certificate' in line:
            state='hit' if 'tlsCacheHit=true' in line else 'miss'
            samples['tls_'+state+'/'+key].append(float(value))
def percentile(values,p):
    ordered=sorted(values)
    return ordered[max(0,math.ceil(len(ordered)*p)-1)]
print(json.dumps({key:{'count':len(values),'p50':percentile(values,.5),'p95':percentile(values,.95),'p99':percentile(values,.99),'max':max(values)} for key,values in sorted(samples.items())},indent=2))
