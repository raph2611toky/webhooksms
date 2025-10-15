from flask import Flask, request, jsonify, render_template_string
from flask_cors import CORS
from datetime import datetime
import json

app = Flask(__name__)
CORS(app)

messages = []

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SMS/MMS Webhook Logs</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <meta http-equiv="refresh" content="10">  <!-- Auto-refresh toutes 10s -->
    <style>
        body { padding: 20px; }
        table { word-break: break-all; }
        .json-pre { white-space: pre-wrap; background: #f8f9fa; padding: 10px; border: 1px solid #ddd; }
    </style>
</head>
<body>
    <div class="container">
        <h1 class="my-4">Webhook SMS/MMS Reçus ({{ total }} messages)</h1>
        <p>Dernière mise à jour : {{ now }}</p>
        {% if messages %}
        <table class="table table-striped table-bordered">
            <thead class="table-dark">
                <tr>
                    <th>#</th>
                    <th>Type</th>
                    <th>Expéditeur</th>
                    <th>Corps/Body</th>
                    <th>Date</th>
                    <th>Autres Champs (JSON)</th>
                    {% if has_mms %}<th>Pièces Jointes MMS</th>{% endif %}
                </tr>
            </thead>
            <tbody>
                {% for msg in messages %}
                <tr>
                    <td>{{ loop.index }}</td>
                    <td>{{ msg.get('msg_type', 'Inconnu') }}</td>
                    <td>{{ msg.get('address', msg.get('senderAddress', 'N/A')) }}</td>
                    <td>{{ msg.get('body', msg.get('text', 'N/A')) | truncate(100) }}</td>
                    <td>{{ format_date(msg.get('date', 0)) }}</td>
                    <td class="json-pre"><small>{{ pretty_other_fields(msg) }}</small></td>
                    {% if msg.get('parts') %}
                    <td>
                        <ul>
                        {% for part in msg['parts'] %}
                            <li>Type: {{ part.get('ct', 'N/A') }}, Text: {{ part.get('text', 'N/A') }}, Nom: {{ part.get('name', 'N/A') }}</li>
                        {% endfor %}
                        </ul>
                    </td>
                    {% endif %}
                </tr>
                {% endfor %}
            </tbody>
        </table>
        {% else %}
        <p class="alert alert-info">Aucun message reçu pour le moment. Envoyez un SMS/MMS à votre appareil Android !</p>
        {% endif %}
    </div>
</body>
</html>
"""

def format_date(timestamp):
    """Convertit timestamp ms ou sec en date lisible"""
    if timestamp > 10000000000:  # ms
        return datetime.fromtimestamp(timestamp / 1000).strftime('%Y-%m-%d %H:%M:%S')
    else:  # sec (MMS)
        return datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d %H:%M:%S')

def pretty_other_fields(msg):
    """Exclut champs affichés, pretty print le reste"""
    exclude = ['msg_type', 'address', 'senderAddress', 'body', 'text', 'date', 'parts', 'recipient']
    other = {k: v for k, v in msg.items() if k not in exclude}
    return json.dumps(other, indent=2, ensure_ascii=False) if other else 'N/A'

@app.route('/sms', methods=['POST'])
def webhook_sms():
    if not request.is_json:
        return jsonify({"error": "JSON requis"}), 400
    
    data = request.get_json()
    data['received_at'] = datetime.now().isoformat()
    
    messages.append(data)
    print(f"Nouveau message reçu : {data.get('msg_type')} de {data.get('address')}")  # Log console
    
    return jsonify({"status": "reçu", "id": len(messages)}), 200

@app.route('/', methods=['GET'])
def list_messages():
    has_mms = any('parts' in msg for msg in messages)
    return render_template_string(
        HTML_TEMPLATE,
        messages=messages[::-1],  # Plus récent en haut
        total=len(messages),
        now=datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        has_mms=has_mms,
        format_date=format_date,
        pretty_other_fields=pretty_other_fields
    )

if __name__ == '__main__':
    print("Serveur Flask démarré sur http://192.168.0.107:5000")
    print("Endpoint webhook: POST /sms")
    print("Listing: GET /")
    app.run(host='0.0.0.0', port=5000, debug=True)