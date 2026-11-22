export default function handler(req, res) {
    return res.status(404).json({
        error: 'Not Found',
        message: 'The requested API endpoint does not exist.'
    });
}
