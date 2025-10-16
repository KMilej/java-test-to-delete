

curl -iX GET 'http://localhost:8002'
curl -iX GET 'http://localhost:8002/token-check'
curl -iX GET 'http://localhost:8002/token-binding-check'

sleep 2

TOKEN=$(approov token -genExample api.example.com | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -n1)

curl -iX GET 'http://localhost:8002' \
  -H "Approov-Token: ${TOKEN}"
curl -iX GET 'http://localhost:8002/token-check' \
  -H "Approov-Token: ${TOKEN}"
curl -iX GET 'http://localhost:8002/token-binding-check' \
  -H "Approov-Token: ${TOKEN}"


sleep 5
Value=$(Kmilej)

BindingToken=$(approov token -setDataHashinToken kmilej -genExample api.example.com | grep -oE '[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' | head -n1)

curl -iX GET "http://localhost:8002' \
-H "Approov-Token: ${BindingToken}" \
-H "header "Authorization: ${Value}"

curl -iX GET "http://localhost:8002/token-check' \
-H "Approov-Token: ${BindingToken}" \
-H "header "Authorization: ${Value}"

curl -iX GET "http://localhost:8002/token-binding-check' \
-H "Approov-Token: ${BindingToken}" \
-H "header "Authorization: ${Value}"






